from flask import Blueprint, request, jsonify

admin_bp = Blueprint("admin_bp", __name__)


@admin_bp.route("/api/v1/admin/kits", methods=["POST"])
def create_kit():
    """
    Register a new IoT device kit in Supabase.
    Kit is created without a farm assignment (farm_id = NULL).
    Assignment is a separate step via POST /api/v1/admin/kits/assign.

    Required: deviceId
    Optional: name (derived from kitId if absent), kitId, serialNumber,
              firmwareVersion, thingspeakChannelId, thingspeakReadApiKey,
              thingspeakWriteApiKey (stored server-side only — never returned).
    """
    try:
        from data.supabase_client import create_device_kit
        data = request.get_json() or {}

        device_id = (data.get("deviceId") or data.get("device_id") or "").strip()
        if not device_id:
            return jsonify({"status": "error", "message": "deviceId is a required field"}), 400

        # Auto-derive a friendly name if caller omits it
        kit_id = (data.get("kitId") or data.get("kit_id") or "").strip()
        name = (data.get("name") or "").strip() or (
            "{} ESP32 Controller".format(kit_id) if kit_id else "{} Controller".format(device_id)
        )

        print("[ADMIN KIT CREATE] deviceId={!r} kitId={!r}".format(device_id, kit_id))

        result = create_device_kit(
            device_id=device_id,
            name=name,
            kit_id=kit_id,
            serial_number=(data.get("serialNumber") or data.get("serial_number") or "").strip(),
            firmware_version=(data.get("firmwareVersion") or data.get("firmware_version") or "").strip(),
            thingspeak_channel_id=data.get("thingspeakChannelId") or data.get("thingspeak_channel_id") or None,
            thingspeak_read_api_key=data.get("thingspeakReadApiKey") or data.get("thingspeak_read_api_key") or None,
            thingspeak_write_api_key=data.get("thingspeakWriteApiKey") or data.get("thingspeak_write_api_key") or None,
        )

        status = result.get("status")
        if status == "error":
            print("[ADMIN KIT CREATE] failed: {}".format(result.get("message")))
            return jsonify({"status": "error", "message": result.get("message")}), 400

        if status == "fallback":
            # Supabase not configured — do NOT allow local-only kit creation
            print("[ADMIN KIT CREATE] failed: Supabase not configured (fallback mode)")
            return jsonify({
                "status": "error",
                "message": "Backend database is not configured. Cannot register kit without Supabase."
            }), 503

        # Strip write API key from response — it is server-side only
        response_data = dict(result.get("data") or {})
        response_data.pop("thingspeak_write_api_key", None)

        print("[ADMIN KIT CREATE] success: deviceId={!r}".format(device_id))
        return jsonify({
            "status": "success",
            "message": "Device kit registered successfully.",
            "data": response_data
        }), 201

    except Exception as e:
        print("[ADMIN KIT CREATE] exception: {}".format(e))
        return jsonify({"status": "error", "message": "Internal server error: {}".format(str(e))}), 500


@admin_bp.route("/api/v1/admin/kits/assign", methods=["POST"])
def assign_kit():
    """
    Assign a device kit to a farmer's farm.
    The backend resolves the authoritative farm_id from farm_members using the
    farmer profile UUID. Android must NEVER pass farm_id directly.

    Required: deviceId, farmerProfileId
    Returns 409 Conflict if the device is already assigned to a different farm.
    """
    try:
        from data.supabase_client import assign_device_to_farm
        data = request.get_json() or {}

        device_id = (data.get("deviceId") or data.get("device_id") or "").strip()
        farmer_profile_id = (data.get("farmerProfileId") or data.get("farmer_profile_id") or "").strip()

        if not device_id or not farmer_profile_id:
            return jsonify({"status": "error", "message": "deviceId and farmerProfileId are required fields"}), 400

        print("[ADMIN KIT ASSIGN] device={!r} farmer={!r}".format(device_id, farmer_profile_id))

        result = assign_device_to_farm(device_id=device_id, farmer_profile_id=farmer_profile_id)
        status = result.get("status")

        if status == "error":
            msg = result.get("message", "Assignment failed")
            print("[ADMIN KIT ASSIGN] failed: {}".format(msg))
            # 409 Conflict when device is already assigned
            if "already assigned" in msg.lower():
                return jsonify({"status": "error", "message": msg}), 409
            return jsonify({"status": "error", "message": msg}), 400

        if status == "fallback":
            print("[ADMIN KIT ASSIGN] failed: Supabase not configured (fallback mode)")
            return jsonify({
                "status": "error",
                "message": "Backend database is not configured. Cannot assign kit without Supabase."
            }), 503

        assignment = result.get("data", {})
        print(
            "[ADMIN KIT ASSIGN] device={!r} farmer={!r} farm={!r}".format(
                device_id, farmer_profile_id, assignment.get("farmId")
            )
        )
        print("[ADMIN KIT ASSIGN] success")

        return jsonify({
            "status": "success",
            "message": "Device '{}' assigned to farm '{}' successfully.".format(
                device_id, assignment.get("farmId")
            ),
            "data": assignment
        }), 200

    except Exception as e:
        print("[ADMIN KIT ASSIGN] exception: {}".format(e))
        return jsonify({"status": "error", "message": "Internal server error: {}".format(str(e))}), 500


@admin_bp.route("/api/v1/admin/kits", methods=["GET"])
def list_kits():
    """
    Fetch all registered device kits from Supabase for the admin panel.
    thingspeak_write_api_key is NEVER returned.
    thingspeak_read_api_key is included — admin needs it to verify ThingSpeak setup.
    """
    try:
        from data.supabase_client import get_all_kits
        kits = get_all_kits()
        return jsonify({"status": "success", "data": kits}), 200
    except Exception as e:
        print("[ADMIN KIT LIST] exception: {}".format(e))
        return jsonify({"status": "error", "message": "Internal server error: {}".format(str(e))}), 500


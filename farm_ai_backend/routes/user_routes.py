from flask import Blueprint, request, jsonify
from data.supabase_client import (
    sync_user_profile,
    supabase_register_user,
    supabase_login_user,
    supabase_forgot_password,
    get_coordinates_for_location
)

user_bp = Blueprint("user_bp", __name__)

@user_bp.route('/users/sync', methods=['POST'])
def sync_user():
    try:
        data = request.get_json() or {}
        uid = data.get('uid')
        name = data.get('name')
        email = data.get('email')
        role = data.get('role')
        farm_name = data.get('farmName')
        farm_location = data.get('farmLocation')
        phone = data.get('phone')
        
        # Veterinarian-specific fields
        specialty = data.get('specialty')
        location = data.get('location')
        photo_url = data.get('photoUrl')
        license_number = data.get('licenseNumber')
        qualification = data.get('qualification')
        experience = data.get('experience')
        
        # Coordinates
        latitude = data.get('latitude')
        longitude = data.get('longitude')
        
        if not uid or not name or not email or not role:
            return jsonify({'error': 'uid, name, email, and role are required parameters'}), 400
            
        res = sync_user_profile(
            uid=uid,
            name=name,
            email=email,
            role=role,
            farm_name=farm_name,
            farm_location=farm_location,
            phone=phone,
            specialty=specialty,
            location=location,
            photo_url=photo_url,
            license_number=license_number,
            qualification=qualification,
            experience=experience,
            latitude=latitude,
            longitude=longitude
        )
        
        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400
            
        return jsonify({
            'status': 'success',
            'message': 'Profile synchronized successfully',
            'profile_id': res.get('profile_id')
        }), 200
        
    except Exception as e:
        print(f"[User API] Exception in sync_user: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500
 
 
@user_bp.route('/auth/register', methods=['POST'])
def auth_register():
    try:
        data = request.get_json() or {}
        name = data.get('name')
        email = data.get('email')
        password = data.get('password')
        role = data.get('role')
        farm_name = data.get('farmName')
        farm_location = data.get('farmLocation')
        total_sheds = data.get('totalSheds', 4)
        floor_space_sq_ft = data.get('floorSpaceSqFt', 24000)
        
        # Veterinarian-specific fields
        specialty = data.get('specialty')
        phone = data.get('phone')
        location = data.get('location')
        photo_url = data.get('photoUrl')
        license_number = data.get('licenseNumber')
        qualification = data.get('qualification')
        experience = data.get('experience')
        
        # Coordinates
        latitude = data.get('latitude')
        longitude = data.get('longitude')
        
        if not name or not email or not password or not role:
            return jsonify({'error': 'name, email, password, and role are required fields'}), 400
            
        res = supabase_register_user(
            name=name,
            email=email,
            password=password,
            role=role,
            farm_name=farm_name,
            farm_location=farm_location,
            total_sheds=total_sheds,
            floor_space_sq_ft=floor_space_sq_ft,
            specialty=specialty,
            phone=phone,
            location=location,
            photo_url=photo_url,
            license_number=license_number,
            qualification=qualification,
            experience=experience,
            latitude=latitude,
            longitude=longitude
        )
        
        if res.get('status') == 'error':
            return jsonify({
                'status': 'error',
                'message': res.get('message'),
                'code': res.get('code')
            }), 400
            
        return jsonify(res), 200
    except Exception as e:
        print(f"[Auth API] Exception in auth_register: {e}")
        return jsonify({'status': 'error', 'message': f"Internal server error: {str(e)}"}), 500


@user_bp.route('/auth/login', methods=['POST'])
def auth_login():
    try:
        data = request.get_json() or {}
        email = data.get('email')
        password = data.get('password')
        
        if not email or not password:
            return jsonify({'error': 'email and password are required fields'}), 400
            
        res = supabase_login_user(email=email, password=password)
        
        if res.get('status') == 'error':
            return jsonify({
                'status': 'error',
                'message': res.get('message'),
                'code': res.get('code')
            }), 400
            
        return jsonify(res), 200
    except Exception as e:
        print(f"[Auth API] Exception in auth_login: {e}")
        return jsonify({'status': 'error', 'message': f"Internal server error: {str(e)}"}), 500


@user_bp.route('/auth/forgot-password', methods=['POST'])
def auth_forgot_password():
    try:
        data = request.get_json() or {}
        email = data.get('email')
        
        if not email:
            return jsonify({'error': 'email is a required field'}), 400
            
        res = supabase_forgot_password(email=email)
        
        if res.get('status') == 'error':
            return jsonify({'status': 'error', 'message': res.get('message')}), 400
            
        return jsonify(res), 200
    except Exception as e:
        print(f"[Auth API] Exception in auth_forgot_password: {e}")
        return jsonify({'status': 'error', 'message': f"Internal server error: {str(e)}"}), 500


@user_bp.route('/admin/pending-farmers', methods=['GET'])
def get_pending_farmers_route():
    try:
        from data.supabase_client import get_pending_farmers
        farmers = get_pending_farmers()
        return jsonify({
            "status": "success",
            "data": farmers
        }), 200
    except Exception as e:
        print(f"[Auth API] Error in get_pending_farmers_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


@user_bp.route('/admin/review-farmer', methods=['POST'])
def review_farmer_route():
    try:
        data = request.get_json() or {}
        profile_id = data.get('profileId')
        action = data.get('action') # 'APPROVE' or 'REJECT'
        rejection_reason = data.get('rejectionReason')
        
        if not profile_id or not action:
            return jsonify({"status": "error", "message": "profileId and action are required fields"}), 400
            
        from data.supabase_client import review_farmer
        res = review_farmer(profile_id, action, rejection_reason)
        
        if res.get('status') == 'error':
            return jsonify({"status": "error", "message": res.get('message')}), 400
            
        return jsonify(res), 200
    except Exception as e:
        print(f"[Auth API] Error in review_farmer_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


@user_bp.route('/admin/farmers', methods=['GET'])
def get_all_farmers_route():
    try:
        from data.supabase_client import get_all_farmers
        farmers = get_all_farmers()
        return jsonify({
            "status": "success",
            "data": farmers
        }), 200
    except Exception as e:
        print(f"[Auth API] Error in get_all_farmers_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


@user_bp.route('/veterinarians', methods=['GET'])
def get_all_veterinarians_route():
    try:
        from data.supabase_client import get_all_veterinarians
        vets = get_all_veterinarians()
        return jsonify({
            "status": "success",
            "data": vets
        }), 200
    except Exception as e:
        print(f"[Auth API] Error in get_all_veterinarians_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


@user_bp.route('/users/<profile_id>/context', methods=['GET'])
def get_user_context(profile_id):
    try:
        from data.supabase_client import supabase
        if not supabase:
            # Fallback for offline mock testing if supabase isn't connected
            from data.supabase_client import _mock_veterinarians
            if profile_id in _mock_veterinarians:
                vet = _mock_veterinarians[profile_id]
                return jsonify({
                    "status": "success",
                    "data": {
                        "profileId": profile_id,
                        "name": vet.get("name"),
                        "email": vet.get("email"),
                        "role": "VETERINARIAN",
                        "phone": vet.get("phone"),
                        "location": vet.get("location"),
                        "specialty": vet.get("specialty"),
                        "photoUrl": vet.get("photo_url"),
                        "licenseNumber": vet.get("license_number"),
                        "qualification": vet.get("qualification"),
                        "experience": vet.get("experience")
                    }
                }), 200

            return jsonify({
                "status": "success",
                "data": {
                    "profileId": profile_id,
                    "name": "Fallback User",
                    "email": "user@example.com",
                    "role": "FARMER",
                    "farmId": "farm_fallback",
                    "farmName": "Fallback Farm",
                    "deviceId": "esp32_devkitc_fallback",
                    "deviceName": "Fallback ESP32 Node",
                    "thingspeakChannelId": "12345",
                    "thingspeakReadApiKey": "KEY123",
                    "wifiSsid": "Simulated-WiFi"
                }
            }), 200

        # Fetch profile
        profile_res = supabase.table("profiles").select("*").eq("id", profile_id).execute()
        if not profile_res.data:
            return jsonify({'error': 'Profile not found'}), 404

        profile = profile_res.data[0]
        role = profile.get("role", "FARMER")

        context_data = {
            "profileId": profile_id,
            "name": profile.get("name"),
            "email": profile.get("email"),
            "role": role,
            "farmId": None,
            "farmName": None,
            "deviceId": None,
            "deviceName": None,
            "thingspeakChannelId": None,
            "thingspeakReadApiKey": None,
            "latitude": None,
            "longitude": None
        }

        if role == "FARMER":
            member_res = supabase.table("farm_members").select("farm_id").eq("profile_id", profile_id).execute()
            if member_res.data:
                farm_id = member_res.data[0].get("farm_id")
                context_data["farmId"] = farm_id

                farm_res = supabase.table("farms").select("*").eq("id", farm_id).execute()
                if farm_res.data:
                    farm = farm_res.data[0]
                    lat = farm.get("latitude")
                    lng = farm.get("longitude")
                    if lat is None or lng is None:
                        loc_str = farm.get("location") or farm.get("name") or profile.get("name")
                        lat, lng = get_coordinates_for_location(loc_str, profile_id)
                        try:
                            supabase.table("farms").update({
                                "latitude": lat,
                                "longitude": lng
                            }).eq("id", farm_id).execute()
                        except Exception:
                            pass
                    context_data["farmName"] = farm.get("name")
                    context_data["latitude"] = lat
                    context_data["longitude"] = lng

                device_res = supabase.table("devices").select("*").eq("farm_id", farm_id).execute()
                if device_res.data:
                    device = device_res.data[0]
                    device_id = device.get("id")
                    context_data["deviceId"] = device_id
                    context_data["deviceName"] = device.get("name")
                    context_data["thingspeakChannelId"] = device.get("thingspeak_channel_id")
                    context_data["thingspeakReadApiKey"] = device.get("thingspeak_read_api_key")
                    
                    context_data["wifiSsid"] = None
                    try:
                        wifi_res = supabase.table("device_wifi_configs").select("wifi_ssid").eq("device_id", device_id).execute()
                        if wifi_res.data:
                            context_data["wifiSsid"] = wifi_res.data[0].get("wifi_ssid")
                    except Exception as we:
                        print(f"[Auth API] Error fetching device wifi config: {we}")

        elif role == "VETERINARIAN":
            vet_res = supabase.table("veterinarians").select("*").eq("id", profile_id).execute()
            if vet_res.data:
                vet = vet_res.data[0]
                lat = vet.get("latitude")
                lng = vet.get("longitude")
                if lat is None or lng is None:
                    loc_str = vet.get("location") or vet.get("name")
                    lat, lng = get_coordinates_for_location(loc_str, profile_id)
                    try:
                        supabase.table("veterinarians").update({
                            "latitude": lat,
                            "longitude": lng
                        }).eq("id", profile_id).execute()
                    except Exception:
                        pass
                context_data["phone"] = vet.get("phone")
                context_data["location"] = vet.get("location")
                context_data["latitude"] = lat
                context_data["longitude"] = lng
                context_data["specialty"] = vet.get("specialty")
                context_data["photoUrl"] = vet.get("photo_url")
                context_data["licenseNumber"] = vet.get("license_number")
                context_data["qualification"] = vet.get("qualification")
                context_data["experience"] = vet.get("experience")

        return jsonify({
            "status": "success",
            "data": context_data
        }), 200
    except Exception as e:
        print(f"[Auth API] Error in get_user_context: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


# API endpoints for alerts and cases
@user_bp.route("/health-alerts", methods=["GET"])
@user_bp.route("/alerts", methods=["GET"])
def api_get_alerts():
    batch_id = request.args.get("batch_id")
    from data.supabase_client import get_alerts
    data = get_alerts(batch_id)
    return jsonify({"status": "success", "data": data}), 200

@user_bp.route("/health-alerts", methods=["POST"])
@user_bp.route("/alerts", methods=["POST"])
def api_create_alert():
    try:
        req = request.json
        batch_id = req.get("batchId")
        device_id = req.get("deviceId")
        prediction_id = req.get("predictionId")
        title = req.get("title")
        description = req.get("description")
        severity = req.get("severity")
        from data.supabase_client import create_db_alert
        alert = create_db_alert(batch_id, device_id, prediction_id, title, description, severity)
        if alert:
            return jsonify({"status": "success", "data": alert}), 200
        return jsonify({"status": "error", "message": "Failed to create alert"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@user_bp.route("/health-alerts/<alert_id>", methods=["PUT"])
@user_bp.route("/alerts/<alert_id>", methods=["PUT"])
def api_update_alert_status(alert_id):
    try:
        req = request.json
        status = req.get("status")
        from data.supabase_client import update_alert_status
        success = update_alert_status(alert_id, status)
        if success:
            return jsonify({"status": "success", "message": "Alert status updated"}), 200
        return jsonify({"status": "error", "message": "Failed to update alert"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@user_bp.route("/veterinary-consultations", methods=["GET"])
@user_bp.route("/cases", methods=["GET"])
def api_get_cases():
    vet_id = request.args.get("vet_id")
    batch_id = request.args.get("batch_id")
    from data.supabase_client import get_cases
    data = get_cases(vet_id, batch_id)
    return jsonify({"status": "success", "data": data}), 200

@user_bp.route("/veterinary-consultations", methods=["POST"])
@user_bp.route("/cases", methods=["POST"])
def api_create_case():
    try:
        req = request.json
        alert_id = req.get("alertId")
        batch_id = req.get("batchId")
        veterinarian_id = req.get("veterinarianId")
        status = req.get("status")
        from data.supabase_client import create_case
        case = create_case(alert_id, batch_id, veterinarian_id, status)
        if case:
            return jsonify({"status": "success", "data": case}), 200
        return jsonify({"status": "error", "message": "Failed to create case"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@user_bp.route("/veterinary-consultations/<case_id>", methods=["PUT"])
@user_bp.route("/cases/<case_id>", methods=["PUT"])
def api_update_case(case_id):
    try:
        req = request.json
        veterinarian_id = req.get("veterinarianId")
        status = req.get("status")
        diagnosis = req.get("diagnosis")
        recommendation = req.get("recommendation")
        treatment = req.get("treatment")
        follow_up_instructions = req.get("followUpInstructions")
        from data.supabase_client import update_case
        success = update_case(
            case_id=case_id,
            veterinarian_id=veterinarian_id,
            status=status,
            diagnosis=diagnosis,
            recommendation=recommendation,
            treatment=treatment,
            follow_up_instructions=follow_up_instructions
        )
        if success:
            return jsonify({"status": "success", "message": "Case updated successfully"}), 200
        return jsonify({"status": "error", "message": "Failed to update case"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500



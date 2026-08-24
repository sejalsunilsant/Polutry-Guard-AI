from flask import Blueprint, request, jsonify
from data.supabase_client import create_batch, get_active_batch, update_batch_status, get_all_batches, record_mortality
from datetime import date

batch_bp = Blueprint("batch_bp", __name__)

@batch_bp.route('/api/v1/farms/<farm_id>/batches', methods=['POST'])
def start_poultry_batch(farm_id):
    try:
        data = request.get_json() or {}
        batch_id = data.get('batchId') or data.get('batch_id')
        start_date = data.get('startDate') or data.get('start_date') or date.today().strftime("%Y-%m-%d")
        initial_count = data.get('initialCount') or data.get('initial_count')
        breed = data.get('breed', 'Broiler')
        
        if not batch_id or not initial_count:
            return jsonify({'error': 'batchId and initialCount are required parameters'}), 400
            
        try:
            initial_count = int(initial_count)
            if initial_count <= 0:
                return jsonify({'error': 'initialCount must be greater than 0'}), 400
        except ValueError:
            return jsonify({'error': 'initialCount must be a valid integer'}), 400

        # Check if there is already an active batch on this farm
        active_batch = get_active_batch(farm_id)
        if active_batch:
            return jsonify({
                'error': f"Cannot start a new batch. Batch '{active_batch.get('id')}' is currently active on farm '{farm_id}'."
            }), 400

        res = create_batch(
            batch_id=batch_id,
            farm_id=farm_id,
            start_date=start_date,
            initial_count=initial_count,
            breed=breed
        )

        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400

        return jsonify({
            'status': 'success',
            'message': f"Batch '{batch_id}' started successfully on farm '{farm_id}'.",
            'data': res.get('data')
        }), 201

    except Exception as e:
        print(f"[Batch API] Exception in start_poultry_batch: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@batch_bp.route('/api/v1/farms/<farm_id>/batches/active', methods=['GET'])
def get_active_poultry_batch(farm_id):
    try:
        active_batch = get_active_batch(farm_id)
        # Return 200 with null data when no active batch exists.
        # 404 is semantically incorrect here — the route exists; the batch simply hasn't started yet.
        # The Android BatchRepository handles data=null as "no active batch" gracefully.
        return jsonify({
            'status': 'success',
            'data': active_batch  # None serialises to JSON null
        }), 200

    except Exception as e:
        print(f"[Batch API] Exception in get_active_poultry_batch: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@batch_bp.route('/api/v1/batches/<batch_id>/status', methods=['PUT'])
def close_poultry_batch(batch_id):
    try:
        data = request.get_json() or {}
        status = data.get('status')
        end_date = data.get('endDate') or data.get('end_date') or date.today().strftime("%Y-%m-%d")
        current_count = data.get('currentCount') or data.get('current_count')

        if not status or status not in ('SOLD', 'CLOSED'):
            return jsonify({'error': "Status is required and must be either 'SOLD' or 'CLOSED'"}), 400

        if current_count is not None:
            try:
                current_count = int(current_count)
                if current_count < 0:
                    return jsonify({'error': 'currentCount cannot be negative'}), 400
            except ValueError:
                return jsonify({'error': 'currentCount must be a valid integer'}), 400

        res = update_batch_status(
            batch_id=batch_id,
            status=status,
            end_date=end_date,
            current_count=current_count
        )

        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400

        return jsonify({
            'status': 'success',
            'message': f"Batch '{batch_id}' updated successfully to status '{status}'."
        }), 200

    except Exception as e:
        print(f"[Batch API] Exception in close_poultry_batch: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@batch_bp.route('/api/v1/farms/<farm_id>/batches', methods=['GET'])
def get_all_poultry_batches(farm_id):
    try:
        batches = get_all_batches(farm_id)
        return jsonify({
            'status': 'success',
            'data': batches
        }), 200
    except Exception as e:
        print(f"[Batch API] Exception in get_all_poultry_batches: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500


@batch_bp.route('/api/v1/batches/<batch_id>/mortality', methods=['POST'])
def record_batch_mortality(batch_id):
    try:
        data = request.get_json() or {}
        death_count = data.get('deathCount') or data.get('death_count')
        
        if death_count is None:
            return jsonify({'error': 'deathCount is required'}), 400
        try:
            death_count = int(death_count)
            if death_count <= 0:
                return jsonify({'error': 'deathCount must be greater than 0'}), 400
        except ValueError:
            return jsonify({'error': 'deathCount must be a valid integer'}), 400
            
        record_id = data.get('id')
        reason = data.get('reason') or data.get('suspected_cause') or data.get('suspectedCause') or "Unspecified"
        notes = data.get('notes') or data.get('symptoms') or ""
        recorded_at = data.get('recorded_at') or data.get('recordedAt') or data.get('timestamp')
        recorded_by = data.get('recorded_by') or data.get('recordedBy') or "Farmer"
            
        res = record_mortality(
            batch_id=batch_id,
            death_count=death_count,
            record_id=record_id,
            reason=reason,
            notes=notes,
            recorded_at=recorded_at,
            recorded_by=recorded_by
        )
        
        if res.get('status') == 'error':
            return jsonify({'error': res.get('message')}), 400
            
        return jsonify({
            'status': 'success',
            'message': f"Logged {death_count} deaths for batch '{batch_id}'.",
            'data': res.get('data')
        }), 200
    except Exception as e:
        print(f"[Batch API] Exception in record_batch_mortality: {e}")
        return jsonify({'error': f"Internal server error: {str(e)}"}), 500

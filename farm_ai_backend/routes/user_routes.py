from flask import Blueprint, request, jsonify
from data.supabase_client import (
    sync_user_profile,
    supabase_register_user,
    supabase_login_user,
    supabase_forgot_password
)

user_bp = Blueprint("user_bp", __name__)

@user_bp.route('/api/v1/users/sync', methods=['POST'])
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
        
        if not uid or not name or not email or not role:
            return jsonify({'error': 'uid, name, email, and role are required parameters'}), 400
            
        res = sync_user_profile(
            uid=uid,
            name=name,
            email=email,
            role=role,
            farm_name=farm_name,
            farm_location=farm_location,
            phone=phone
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


@user_bp.route('/api/v1/auth/register', methods=['POST'])
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
            floor_space_sq_ft=floor_space_sq_ft
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


@user_bp.route('/api/v1/auth/login', methods=['POST'])
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


@user_bp.route('/api/v1/auth/forgot-password', methods=['POST'])
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


@user_bp.route('/api/v1/admin/pending-farmers', methods=['GET'])
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


@user_bp.route('/api/v1/admin/review-farmer', methods=['POST'])
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

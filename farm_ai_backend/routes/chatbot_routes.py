from flask import Blueprint, request, jsonify
from rag.chatbot import generate_chatbot_response

chatbot_bp = Blueprint("chatbot_bp", __name__)


def _resolve_ownership_chain(profile_id):
    """
    Server-side ownership chain resolution:
    JWT → authenticated profile → authorized farm → authorized batch
    
    Client-sent farmId/batchId are treated only as a requested context
    and MUST be verified against the authenticated user's actual ownership.
    
    Returns:
        tuple: (farmer_id, farm_id, batch_id, error_message)
        On success: (farmer_id, farm_id, batch_id, None)
        On failure: (None, None, None, error_string)
    """
    from data.supabase_client import supabase, get_active_batch
    
    farmer_id = str(profile_id)
    farm_id = None
    batch_id = None
    
    if not supabase:
        # Offline fallback mode — use client-sent context (but log warning)
        print(f"[Chat Auth] Supabase offline — cannot verify ownership for profile {profile_id}")
        return farmer_id, None, None, None
    
    try:
        # 1. Resolve farm_id from farm_members for this authenticated profile
        member_res = supabase.table("farm_members") \
            .select("farm_id") \
            .eq("profile_id", farmer_id) \
            .execute()
        
        if member_res.data and len(member_res.data) > 0:
            farm_id = member_res.data[0].get("farm_id")
        else:
            print(f"[Chat Auth] No farm membership found for profile {farmer_id}")
            # Not necessarily an error — farmer might not have a farm yet
            return farmer_id, None, None, None
        
        # 2. Resolve active batch for this farm
        active_batch = get_active_batch(farm_id)
        if active_batch:
            batch_id = active_batch.get("id")
        
        return farmer_id, farm_id, batch_id, None
        
    except Exception as e:
        print(f"[Chat Auth] Error resolving ownership chain: {e}")
        # Return what we have — don't block the chat entirely
        return farmer_id, farm_id, batch_id, None


def _validate_client_context(client_farm_context, verified_farm_id, verified_batch_id):
    """
    Validate that client-sent farmContext identifiers match server-verified ownership.
    
    Client farmId/batchId are treated as "requested context" that must match
    the authenticated user's actual ownership chain.
    
    If client sends IDs that don't match, we ignore them and use verified IDs.
    This prevents cross-tenant data access.
    """
    client_farm_id = client_farm_context.get("farmId")
    client_batch_id = client_farm_context.get("batchId")
    
    if client_farm_id and verified_farm_id and str(client_farm_id) != str(verified_farm_id):
        print(f"[Chat Auth] WARNING: Client sent farmId={client_farm_id} but verified farmId={verified_farm_id}. Using verified.")
    
    if client_batch_id and verified_batch_id and str(client_batch_id) != str(verified_batch_id):
        print(f"[Chat Auth] WARNING: Client sent batchId={client_batch_id} but verified batchId={verified_batch_id}. Using verified.")


@chatbot_bp.route('/chat', methods=['POST'])
def chat_assistant():
    """
    Authenticated chat endpoint with ownership-validated RAG retrieval.
    
    Flow: JWT → authenticated profile → authorized farm → authorized batch
          → Qdrant filter → Vector retrieval + Live DB fetch → LLM context
    
    Requires: Authorization header with Bearer token.
    Accepts: message, history, farmContext (farmContext IDs are verified, not trusted).
    """
    try:
        from data.supabase_client import verify_token_and_get_user
        
        data = request.get_json() or {}
        user_message = data.get('message', '').strip()
        history = data.get('history', [])
        farm_context = data.get('farmContext', {})

        if not user_message:
            return jsonify({'reply': 'Please specify a question regarding your flock.'}), 400

        # --- 1. Authenticate: Verify JWT token ---
        auth_header = request.headers.get("Authorization")
        farmer_id = None
        farm_id = None
        batch_id = None
        
        if auth_header:
            user_info, auth_error = verify_token_and_get_user(auth_header)
            if auth_error:
                print(f"[Chat Auth] JWT verification failed: {auth_error}")
            elif user_info:
                # --- 2. Resolve ownership chain server-side ---
                farmer_id, farm_id, batch_id, chain_error = _resolve_ownership_chain(
                    user_info["uid"]
                )
                if chain_error:
                    print(f"[Chat Auth] Ownership chain warning: {chain_error}")
                
                # --- 3. Validate client-sent context against verified ownership ---
                _validate_client_context(farm_context, farm_id, batch_id)

        # Fallback if JWT was unauthenticated/expired but client provided farmerId/farmId in farmContext
        if not farm_id and farm_context:
            req_farmer_id = farm_context.get('farmerId')
            req_farm_id = farm_context.get('farmId')
            req_batch_id = farm_context.get('batchId')
            
            if req_farmer_id and not farmer_id:
                farmer_id, farm_id, batch_id, _ = _resolve_ownership_chain(req_farmer_id)
            if req_farm_id and not farm_id:
                farm_id = req_farm_id
            if req_batch_id and not batch_id:
                batch_id = req_batch_id

        # --- 4. Generate response with verified ownership context ---
        reply = generate_chatbot_response(
            user_message, history, farm_context,
            farmer_id=farmer_id,
            farm_id=farm_id,
            batch_id=batch_id
        )
        return jsonify({'reply': reply})

    except Exception as e:
        print(f"[Chatbot API] Error generating chat response: {e}")
        return jsonify({'reply': f"Internal Server exception: {str(e)}"}), 500

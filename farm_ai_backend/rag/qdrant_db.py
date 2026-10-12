import os
import uuid
import hashlib
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Filter, FieldCondition, MatchValue,
    PayloadSchemaType
)

host = os.environ.get("QDRANT_HOST")
api_key = os.environ.get("QDRANT_API_KEY")
collection_name = "poultryguard_knowledge"

client: QdrantClient = None

if host and api_key and host != "your_qdrant_cloud_host" and api_key != "your_qdrant_api_key":
    try:
        # Initialize client with Qdrant Cloud URL and API key
        client = QdrantClient(url=host, api_key=api_key)
        # If client supports fastembed set_model, configure it; otherwise use client directly
        if hasattr(client, "set_model"):
            try:
                client.set_model("sentence-transformers/all-MiniLM-L6-v2")
            except Exception as me:
                print(f"[Qdrant] Note: Fastembed set_model skipped ({me})")
        print(f"[Qdrant] Initialized Qdrant Cloud client at: {host}")
    except Exception as e:
        print(f"[Qdrant] Error initializing Qdrant Cloud client: {e}")
        client = None
else:
    print("[Qdrant] WARNING: QDRANT_HOST or QDRANT_API_KEY missing or unconfigured. Running in local TF-IDF fallback mode.")


def ensure_payload_indexes():
    """
    Create payload indexes on ownership metadata fields for efficient filtering.
    Safe to call multiple times — Qdrant ignores if index already exists.
    """
    if client is None:
        return
    try:
        if not client.collection_exists(collection_name):
            return
        for field in ["data_scope", "farmer_id", "farm_id", "batch_id", "data_type"]:
            try:
                client.create_payload_index(
                    collection_name=collection_name,
                    field_name=field,
                    field_schema=PayloadSchemaType.KEYWORD
                )
            except Exception:
                pass  # Index may already exist
        print("[Qdrant] Payload indexes ensured for ownership metadata fields.")
    except Exception as e:
        print(f"[Qdrant] Error creating payload indexes: {e}")


from fastembed import TextEmbedding
from qdrant_client.models import PointStruct, VectorParams, Distance

_embed_model = None

def get_embed_model():
    global _embed_model
    if _embed_model is None:
        try:
            _embed_model = TextEmbedding(model_name="sentence-transformers/all-MiniLM-L6-v2")
        except Exception as e:
            print(f"[Qdrant] Error initializing TextEmbedding: {e}")
            _embed_model = None
    return _embed_model


def _embed_query(text: str):
    model = get_embed_model()
    if not model:
        return None
    try:
        vecs = list(model.embed([text]))
        if vecs and len(vecs) > 0:
            vec = vecs[0]
            return vec.tolist() if hasattr(vec, "tolist") else list(vec)
    except Exception as e:
        print(f"[Qdrant Embedding Error] Failed to embed text: {e}")
    return None


def search_knowledge_base(query, k=3):
    """
    Search Qdrant Cloud for the top k matching chunks (unfiltered — legacy).
    Returns:
        list of str: Retrieved document chunks.
        None: If client is unconfigured, collection is missing, or query failed.
    """
    if client is None:
        return None
    try:
        # Verify collection exists before querying
        if not client.collection_exists(collection_name):
            print(f"[Qdrant Search] Warning: Collection '{collection_name}' does not exist.")
            return None
            
        query_vec = _embed_query(query)
        if query_vec is None:
            print("[Qdrant Search] Embedding generation failed. Falling back.")
            return None

        # Query points using query_points with fastembed vector
        results = client.query_points(
            collection_name=collection_name,
            query=query_vec,
            using="fast-all-minilm-l6-v2",
            limit=k
        )
        
        chunks = []
        for res in results.points:
            # Check score to ensure relevance (cosine similarity threshold)
            if res.score > 0.35:
                # Document content is stored in metadata/payload as 'document'
                doc = res.payload.get("document") if res.payload else None
                if doc:
                    chunks.append(doc)
        return chunks
    except Exception as e:
        print(f"[Qdrant Search Error] Failed to retrieve chunks for query '{query}': {e}")
        return None


def search_knowledge_base_filtered(query, farmer_id=None, farm_id=None, batch_id=None, k=3):
    """
    Search Qdrant with ownership-based metadata filtering.
    
    Retrieves documents that are EITHER:
    - Global reference documents (data_scope == "global") — accessible to all farmers
    - Farmer-specific documents matching the authenticated farmer's ownership chain
    
    Args:
        query: The search query text.
        farmer_id: Authenticated farmer's profile UUID (server-verified).
        farm_id: Verified farm_id belonging to this farmer.
        batch_id: Verified batch_id belonging to this farm.
        k: Number of results to return.
    
    Returns:
        list of str: Retrieved document chunks, or None if unavailable.
    """
    if client is None:
        return None
    try:
        if not client.collection_exists(collection_name):
            print(f"[Qdrant Filtered Search] Warning: Collection '{collection_name}' does not exist.")
            return None

        from qdrant_client.models import Filter, FieldCondition, MatchValue
        
        should_conditions = [
            FieldCondition(key="data_scope", match=MatchValue(value="global"))
        ]
        
        if farmer_id and farm_id:
            should_conditions.append(
                FieldCondition(key="farmer_id", match=MatchValue(value=str(farmer_id)))
            )

        query_filter = Filter(should=should_conditions)
        
        query_vec = _embed_query(query)
        if query_vec is None:
            return search_knowledge_base(query, k=k)

        results = client.query_points(
            collection_name=collection_name,
            query=query_vec,
            using="fast-all-minilm-l6-v2",
            query_filter=query_filter,
            limit=k
        )
        
        chunks = []
        for res in results.points:
            if res.score > 0.35:
                doc = res.payload.get("document") if res.payload else None
                if doc:
                    chunks.append(doc)
        
        if chunks:
            print(f"[Qdrant Filtered Search] Retrieved {len(chunks)} chunks for farmer={farmer_id}, farm={farm_id}")
        return chunks
    except Exception as e:
        print(f"[Qdrant Filtered Search Error] Failed for query '{query}': {e}")
        # Fall back to unfiltered search if filtered search fails
        return search_knowledge_base(query, k=k)


def generate_deterministic_id(prefix, *parts):
    """
    Generate a deterministic UUID-like ID from a prefix and parts.
    Used for upserting documents so the same logical record always maps to the same Qdrant point.
    """
    raw = f"{prefix}:{'|'.join(str(p) for p in parts)}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def upsert_document(doc_id, text, metadata):
    """
    Upsert a single document into Qdrant with ownership metadata.
    Uses deterministic doc_id to prevent duplicates.
    
    Args:
        doc_id: Deterministic string ID for this document.
        text: The document text to embed.
        metadata: Dict with ownership fields (farmer_id, farm_id, batch_id, data_type, data_scope).
    """
    if client is None:
        return False
    try:
        if not client.collection_exists(collection_name):
            print(f"[Qdrant Upsert] Collection '{collection_name}' does not exist. Skipping.")
            return False
        
        vec = _embed_query(text)
        if vec is None:
            return False

        point = PointStruct(
            id=str(uuid.UUID(hashlib.md5(doc_id.encode('utf-8')).hexdigest())),
            vector={"fast-all-minilm-l6-v2": vec},
            payload={"document": text, **metadata}
        )
        client.upsert(
            collection_name=collection_name,
            points=[point]
        )
        print(f"[Qdrant Upsert] Document '{doc_id}' upserted successfully.")
        return True
    except Exception as e:
        print(f"[Qdrant Upsert Error] Failed to upsert document '{doc_id}': {e}")
        return False


def delete_batch_documents(batch_id):
    """
    Delete all Qdrant points belonging to a specific batch.
    Called when a batch is completed/closed.
    
    Args:
        batch_id: The batch ID whose documents should be removed.
    
    Returns:
        bool: True if deletion succeeded or no points existed.
    """
    if client is None:
        return True  # Nothing to delete
    if not batch_id:
        return True
    try:
        if not client.collection_exists(collection_name):
            return True
        
        # Delete points matching batch_id in metadata
        from qdrant_client.models import FilterSelector
        
        delete_filter = Filter(
            must=[
                FieldCondition(
                    key="batch_id",
                    match=MatchValue(value=str(batch_id))
                )
            ]
        )
        
        client.delete(
            collection_name=collection_name,
            points_selector=FilterSelector(filter=delete_filter)
        )
        print(f"[Qdrant Cleanup] Deleted all points for batch '{batch_id}'.")
        return True
    except Exception as e:
        print(f"[Qdrant Cleanup Error] Failed to delete points for batch '{batch_id}': {e}")
        return False


def recreate_and_index_collection(chunks_list, metadata_list=None):
    """
    Delete the old collection (if exists), recreate it, and upload text chunks.
    
    Args:
        chunks_list: List of text strings to index.
        metadata_list: Optional list of metadata dicts (one per chunk).
                       If None, all chunks get data_scope="global".
    """
    if client is None:
        print("[Qdrant Index] Error: Client is not initialized.")
        return False
    try:
        # Delete if exists
        if client.collection_exists(collection_name):
            client.delete_collection(collection_name)
            print(f"[Qdrant Index] Deleted existing collection: {collection_name}")
            
        # Recreate collection using 384-d cosine vector params
        client.create_collection(
            collection_name=collection_name,
            vectors_config={"fast-all-minilm-l6-v2": VectorParams(size=384, distance=Distance.COSINE)}
        )
        
        # Build metadata: default all to global scope
        if metadata_list is None:
            metadata_list = [{"data_scope": "global", "data_type": "knowledge_base"} for _ in chunks_list]
        
        print(f"[Qdrant Index] Uploading {len(chunks_list)} text chunks to '{collection_name}'...")
        points = []
        for i, (chunk, meta) in enumerate(zip(chunks_list, metadata_list)):
            vec = _embed_query(chunk)
            if vec is not None:
                pt_id = str(uuid.UUID(hashlib.md5(f"chunk_{i}_{chunk[:30]}".encode('utf-8')).hexdigest()))
                points.append(PointStruct(
                    id=pt_id,
                    vector={"fast-all-minilm-l6-v2": vec},
                    payload={"document": chunk, **meta}
                ))
        
        if points:
            client.upsert(
                collection_name=collection_name,
                points=points
            )
        print("[Qdrant Index] Indexing completed successfully!")
        
        # Create payload indexes for efficient filtering
        ensure_payload_indexes()
        
        return True
    except Exception as e:
        print(f"[Qdrant Index Error] Failed to index knowledge base: {e}")
        return False

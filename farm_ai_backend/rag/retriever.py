from sklearn.metrics.pairwise import cosine_similarity
from .vector_db import load_knowledge_base
from .qdrant_db import search_knowledge_base, search_knowledge_base_filtered

def retrieve_relevant_chunks(query, k=3, farmer_id=None, farm_id=None, batch_id=None):
    """
    Search the indexed knowledge chunks using Qdrant Cloud (vector search) with
    ownership-based metadata filtering, falling back to TF-IDF if unconfigured or offline.
    
    Args:
        query: The user's question text.
        k: Number of top chunks to retrieve.
        farmer_id: Verified farmer profile UUID (server-side authenticated).
        farm_id: Verified farm_id belonging to this farmer.
        batch_id: Verified batch_id belonging to this farm.
    
    Returns:
        list of str: Retrieved knowledge chunks.
    """
    # 1. Try Qdrant Cloud with ownership filtering
    if farmer_id or farm_id or batch_id:
        qdrant_chunks = search_knowledge_base_filtered(
            query, 
            farmer_id=farmer_id, 
            farm_id=farm_id, 
            batch_id=batch_id, 
            k=k
        )
    else:
        # No ownership context — search globally (backward compatible)
        qdrant_chunks = search_knowledge_base(query, k=k)
    
    if qdrant_chunks is not None:
        if len(qdrant_chunks) > 0:
            print(f"[RAG Retriever] Successfully retrieved {len(qdrant_chunks)} chunks from Qdrant Cloud.")
            return qdrant_chunks
        else:
            print(f"[RAG Retriever] No chunks matched query '{query}' above similarity threshold. Using local TF-IDF fallback.")
    else:
        # 2. Local TF-IDF Fallback
        print("[RAG Retriever] Qdrant Cloud unavailable or empty. Falling back to local TF-IDF.")
    
    chunks, vectorizer, tfidf_matrix = load_knowledge_base()

    if not chunks or vectorizer is None or tfidf_matrix is None:
        print("[RAG Retriever] Vector database is uninitialized. Returning empty list.")
        return []
        
    try:
        # Preprocess and transform query
        query_vec = vectorizer.transform([query])
        
        # Calculate cosine similarity with all chunks
        similarities = cosine_similarity(query_vec, tfidf_matrix).flatten()
        
        # Get indices of top sorted similarities
        top_indices = similarities.argsort()[::-1]
        
        results = []
        for idx in top_indices:
            # Only include chunks with a non-zero similarity score to ensure relevance
            if similarities[idx] > 0.05:
                results.append(chunks[idx])
                if len(results) >= k:
                    break
                    
        # If no positive matches found, return empty list
        return results
    except Exception as e:
        print(f"[RAG Retriever] Error performing search for query '{query}': {e}")
        return []

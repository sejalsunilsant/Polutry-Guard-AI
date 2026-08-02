import os
from qdrant_client import QdrantClient

host = os.environ.get("QDRANT_HOST")
api_key = os.environ.get("QDRANT_API_KEY")
collection_name = "poultryguard_knowledge"

client: QdrantClient = None

if host and api_key and host != "your_qdrant_cloud_host" and api_key != "your_qdrant_api_key":
    try:
        # Initialize client with Qdrant Cloud URL and API key
        client = QdrantClient(url=host, api_key=api_key)
        
        # Set lightweight embedding model (all-MiniLM-L6-v2 is standard, fast, and uses minimal memory)
        # Using client.set_model() configures automatic text embedding for both add() and query()
        client.set_model("sentence-transformers/all-MiniLM-L6-v2")
        print(f"[Qdrant] Initialized Qdrant Cloud client at: {host}")
    except Exception as e:
        print(f"[Qdrant] Error initializing Qdrant Cloud client: {e}")
        client = None
else:
    print("[Qdrant] WARNING: QDRANT_HOST or QDRANT_API_KEY missing or unconfigured. Running in local TF-IDF fallback mode.")

def search_knowledge_base(query, k=3):
    """
    Search Qdrant Cloud for the top k matching chunks.
    Returns:
        list of str: Retrieved document chunks or empty list if unconfigured/failed.
    """
    if client is None:
        return []
    try:
        # Verify collection exists before querying
        if not client.collection_exists(collection_name):
            print(f"[Qdrant Search] Warning: Collection '{collection_name}' does not exist.")
            return []
            
        # Run query using query_text. QdrantClient handles the embedding generation internally!
        results = client.query(
            collection_name=collection_name,
            query_text=query,
            limit=k
        )
        
        chunks = []
        for res in results:
            # Check score to ensure relevance (cosine similarity threshold)
            if res.score > 0.35:
                # Document content is stored in metadata/payload as 'document'
                doc = res.document
                if doc:
                    chunks.append(doc)
        return chunks
    except Exception as e:
        print(f"[Qdrant Search Error] Failed to retrieve chunks for query '{query}': {e}")
        return []

def recreate_and_index_collection(chunks_list):
    """
    Delete the old collection (if exists), recreate it, and upload text chunks.
    """
    if client is None:
        print("[Qdrant Index] Error: Client is not initialized.")
        return False
    try:
        # Delete if exists
        if client.collection_exists(collection_name):
            client.delete_collection(collection_name)
            print(f"[Qdrant Index] Deleted existing collection: {collection_name}")
            
        # Recreate collection using local/cloud model config.
        # client.add() will recreate or use existing collection.
        # But we create it explicitly to make sure it exists with correct specifications.
        client.create_collection(
            collection_name=collection_name,
            vectors_config=client.get_fastembed_vector_params()
        )
        
        print(f"[Qdrant Index] Uploading {len(chunks_list)} text chunks to '{collection_name}'...")
        # Add documents. client.add automatically extracts embeddings and pushes them.
        client.add(
            collection_name=collection_name,
            documents=chunks_list
        )
        print("[Qdrant Index] Indexing completed successfully!")
        return True
    except Exception as e:
        print(f"[Qdrant Index Error] Failed to index knowledge base: {e}")
        return False

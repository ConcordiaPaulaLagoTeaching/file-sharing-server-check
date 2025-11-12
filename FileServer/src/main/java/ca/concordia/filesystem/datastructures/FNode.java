// In ca/concordia/coen346/filesystem/datastructures/FNode.java

public class FNode {
    
    // The index of the data block storing the file data. Negative if not in use. (2 bytes)
    private short blockIndex; 
    
    // Index to the next FNode, or -1 if no next block. (2 bytes)
    private short nextBlock; 
    
    // Constructor
    public FNode(short blockIndex, short nextBlock) {
        this.blockIndex = blockIndex;
        this.nextBlock = nextBlock;
    }
    
    // Default constructor for an empty/free node
    public FNode() {
        this((short) -1, (short) -1);
    }

    // --- GETTERS AND SETTERS ---
    
    public short getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(short blockIndex) {
        // blockIndex can be negative to indicate a free block (magnitude is the index)
        this.blockIndex = blockIndex;
    }

    public short getNextBlock() {
        return nextBlock;
    }

    public void setNextBlock(short nextBlock) {
        // nextBlock is an index or -1 if it's the last block
        this.nextBlock = nextBlock;
    }
}
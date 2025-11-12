package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance = null; // Should be initialized elsewhere or in a static block if needed
    private final RandomAccessFile disk = null; // Needs proper initialization, setting to null for now
    private final ReentrantLock globalLock = new ReentrantLock();
    
    // Constants
    private static final int FIRST_DATA_BLOCK_INDEX = 1;
    private static final int BLOCK_SIZE = 128; // Example block size

    // File System Metadata (In-Memory Arrays)
    private FEntry[] fentryTable; 
    private FNode[] fnodeTable; 
    private boolean[] freeBlockList; // Bitmap for free data blocks (starts from index 0)
    
    public FileSystemManager(String filename, int totalSize) throws Exception { 
        // Note: Added 'throws Exception' for potential file I/O errors
        // Initialize the file system manager with a file
        if(instance == null) {
            // 1. Initialize in-memory metadata arrays
            this.fentryTable = new FEntry[MAXFILES];
            this.fnodeTable = new FNode[MAXBLOCKS]; 
            this.freeBlockList = new boolean[MAXBLOCKS];

            // 2. Initialize FNode Table (all nodes are unused initially)
            [cite_start]// An unused FNode has a negative blockIndex [cite: 40]
            for (int i = 0; i < MAXBLOCKS; i++) {
                this.fnodeTable[i] = new FNode(-1, -1); 
            }
            
            // 3. Mark the metadata block(s) as used.
            [cite_start]// Block 0 is used for metadata, so data blocks start at index 1 [cite: 47]
            this.freeBlockList[0] = true; 
            
            // TODO: Add actual RandomAccessFile initialization and disk I/O logic here 
            
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
    }

    public void createFile(String filename) throws Exception {
        // 1. Check filename length
        [cite_start]if (filename.length() > 11) { // Filename max length 11 [cite: 27, 100]
            throw new Exception("ERROR: filename too large");
        }

        globalLock.lock(); // Acquire lock for atomic operation 
        try {
            // 2. Check for existing file
            for (FEntry entry : fentryTable) {
                if (entry != null && entry.getFilename().equals(filename)) {
                    throw new Exception("ERROR: file already exists");
                }
            }
            
            // 3. Find a free FEntry slot
            int fentryIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (fentryTable[i] == null) {
                    fentryIndex = i;
                    break;
                }
            }
            if (fentryIndex == -1) {
                throw new Exception("ERROR: No free file entries (MAXFILES limit reached)");
            }

            // 4. Find a free FNode slot (required for the file's 'firstblock' pointer)
            int fnodeIndex = -1;
            for (int i = 0; i < fnodeTable.length; i++) {
                [cite_start]// Unused FNode has negative blockindex [cite: 40]
                if (fnodeTable[i].getBlockIndex() < 0) { 
                    fnodeIndex = i;
                    break;
                }
            }
            if (fnodeIndex == -1) {
                throw new Exception("ERROR: No free file nodes available");
            }
            
            // 5. Allocation and Metadata Update
            
            // Update the allocated FNode: it points to NO data block (-1) and no next node (-1) 
            fnodeTable[fnodeIndex].setBlockIndex(-1); 
            fnodeTable[fnodeIndex].setNext(-1); 

            // Create the new FEntry: size is 0, firstBlock points to the allocated FNode
            fentryTable[fentryIndex] = new FEntry(filename, (short) 0, (short) fnodeIndex);

            // TODO: Call private method to persist fentryTable and fnodeTable to 'disk'
            
            System.out.println("SUCCESS: Created file '" + filename + "' at FEntry[" + fentryIndex + "] with FNode[" + fnodeIndex + "]");

        } finally {
            globalLock.unlock(); // Release lock
        }
    }

    public void deleteFile(String filename) throws Exception {
        
        globalLock.lock(); // Acquire lock for atomic operation 
        try {
            // 1. Find the FEntry
            int fentryIndex = -1;
            FEntry fileEntry = null;
            for (int i = 0; i < MAXFILES; i++) {
                if (fentryTable[i] != null && fentryTable[i].getFilename().equals(filename)) {
                    fentryIndex = i;
                    fileEntry = fentryTable[i];
                    break;
                }
            }
            if (fentryIndex == -1) {
                throw new Exception("ERROR: file does not exist");
            }

            // 2. Traverse and Free FNodes (and their associated data blocks)
            int currentFNodeIndex = fileEntry.getFirstBlock();
            
            while (currentFNodeIndex != -1) {
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                
                int nextFNodeIndex = currentFNode.getNext();
                int dataBlockIndex = currentFNode.getBlockIndex();

                // A. Free the associated data block (if one exists)
                // A data block index >= 1 means the block is in use for file data.
                if (dataBlockIndex >= FIRST_DATA_BLOCK_INDEX) {
                    if (dataBlockIndex < MAXBLOCKS) {
                        freeBlockList[dataBlockIndex] = false; // Mark data block as free
                        // TODO: Optional: Write zeros to the data block on disk
                    } else {
                        // This indicates corruption, but we continue cleaning up metadata
                        System.err.println("WARNING: FNode points to an invalid data block index: " + dataBlockIndex);
                    }
                }
                
                // B. Free the FNode itself (by setting blockIndex to -1)
                currentFNode.setBlockIndex(-1); // Mark FNode as free
                currentFNode.setNext(-1); 
                
                // C. Move to the next FNode in the chain
                currentFNodeIndex = nextFNodeIndex;
            }

            // 3. Release the FEntry
            fentryTable[fentryIndex] = null; // Mark FEntry slot as unused (available for a new file)

            // TODO: Call private method to persist all three metadata structures to 'disk'
            
            System.out.println("SUCCESS: Deleted file '" + filename + "' from FEntry[" + fentryIndex + "]");

        } finally {
            globalLock.unlock(); // Release lock
        }
    }

    public String[] listFiles() {
        globalLock.lock();
        try {
            // Count how many files are currently active
            int count = 0;
            for (FEntry entry : fentryTable) {
                if (entry != null) {
                    count++;
                }
            }
            
            String[] fileNames = new String[count];
            int index = 0;
            
            // Populate the array with the filenames
            for (FEntry entry : fentryTable) {
                if (entry != null) {
                    fileNames[index++] = entry.getFilename();
                }
            }
            
            return fileNames;

        } finally {
            globalLock.unlock();
        }
    }

    private long getDiskBlockOffset(int blockIndex) {
        // Calculates the byte offset on the disk where a specific block starts.
        // Block 0 is reserved for metadata, so data blocks (index >= 1) follow it.
        return (long) blockIndex * BLOCK_SIZE;
    }

    public byte[] read(String filename, int length, int offset) throws Exception {
        
        globalLock.lock();
        try {
            // 1. Find the FEntry
            FEntry fileEntry = null;
            for (FEntry entry : fentryTable) {
                if (entry != null && entry.getFilename().equals(filename)) {
                    fileEntry = entry;
                    break;
                }
            }
            if (fileEntry == null) {
                throw new Exception("ERROR: file does not exist");
            }
            
            // 2. Validate parameters against file size
            if (offset < 0 || offset > fileEntry.getFilesize()) {
                throw new Exception("ERROR: Invalid offset");
            }
            // Actual bytes to read should be limited by file size
            int actualReadLength = Math.min(length, fileEntry.getFilesize() - offset);
            
            if (actualReadLength <= 0) {
                return new byte[0]; // Nothing to read
            }
            
            byte[] resultBuffer = new byte[actualReadLength];
            int currentReadOffset = 0; // Tracks position in the resultBuffer
            
            // 3. Find the starting block and position
            int blocksToSkip = offset / BLOCK_SIZE;
            int startBlockOffset = offset % BLOCK_SIZE; // Byte offset within the first block to read from
            int currentFNodeIndex = fileEntry.getFirstBlock();

            // Check for empty file: An empty file's FNode.blockIndex is -1.
            if (fnodeTable[currentFNodeIndex].getBlockIndex() < 0 && blocksToSkip == 0) {
                 return new byte[0]; 
            }
            
            // 4. Traverse FNodes to reach the starting block
            for (int i = 0; i < blocksToSkip; i++) {
                if (currentFNodeIndex == -1) {
                    throw new Exception("ERROR: File is shorter than reported filesize/offset"); 
                }
                currentFNodeIndex = fnodeTable[currentFNodeIndex].getNext();
            }

            // 5. Read block by block
            int bytesRemainingToRead = actualReadLength;
            
            while (bytesRemainingToRead > 0 && currentFNodeIndex != -1) {
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                int dataBlockIndex = currentFNode.getBlockIndex();
                
                if (dataBlockIndex < FIRST_DATA_BLOCK_INDEX) {
                    throw new Exception("ERROR: FNode chain corruption detected while reading"); 
                }
                
                // Calculate size for this specific read operation
                int bytesInCurrentBlock = BLOCK_SIZE - startBlockOffset;
                int readSize = Math.min(bytesRemainingToRead, bytesInCurrentBlock);
                
                // --- DISK I/O LOGIC (Placeholder: You must implement actual disk.read/seek) ---
                
                byte[] blockData = new byte[readSize]; // Buffer to hold data read from disk
                
                // // TODO: Implement actual disk I/O using RandomAccessFile 'disk'
                // disk.seek(getDiskBlockOffset(dataBlockIndex) + startBlockOffset);
                // disk.read(blockData, 0, readSize);
                
                // Simulate I/O for now:
                // Note: The 'blockData' array will contain zeros until disk I/O is implemented.
                System.out.println("DEBUG: Attempting to read " + readSize + " bytes from block " + dataBlockIndex);
                
                // Copy the read data (currently zeros) into the final result buffer
                System.arraycopy(blockData, 0, resultBuffer, currentReadOffset, readSize);
                
                // --- END DISK I/O ---
                
                // Update tracking variables
                bytesRemainingToRead -= readSize;
                currentReadOffset += readSize;
                startBlockOffset = 0; // After the first block, subsequent blocks read start from offset 0
                
                // Move to the next FNode
                currentFNodeIndex = currentFNode.getNext();
            }
            
            // If the loop finished without reading all requested bytes (e.g., end of file reached early)
            if (currentReadOffset < actualReadLength) {
                byte[] partialResult = new byte[currentReadOffset];
                System.arraycopy(resultBuffer, 0, partialResult, 0, currentReadOffset);
                return partialResult;
            }
            
            return resultBuffer;

        } finally {
            globalLock.unlock();
        }
    }

    private int findFreeDataBlock() {
        // Find the first UNUSED data block (index >= 1)
        for (int i = FIRST_DATA_BLOCK_INDEX; i < MAXBLOCKS; i++) {
            if (!freeBlockList[i]) {
                freeBlockList[i] = true; // Mark as USED immediately upon finding
                return i;
            }
        }
        return -1; // No free blocks
    }

    private int findFreeFNode() {
        // Find the first UNUSED FNode (blockIndex < 0)
        for (int i = 0; i < fnodeTable.length; i++) {
            if (fnodeTable[i].getBlockIndex() < 0) {
                return i;
            }
        }
        return -1; // No free FNodes
    }

    public void write(String filename, byte[] data, int offset) throws Exception {
        
        if (data.length == 0) return; // Nothing to write

        globalLock.lock();
        try {
            // 1. Find the FEntry
            FEntry fileEntry = null;
            for (FEntry entry : fentryTable) {
                if (entry != null && entry.getFilename().equals(filename)) {
                    fileEntry = entry;
                    break;
                }
            }
            if (fileEntry == null) {
                throw new Exception("ERROR: file does not exist");
            }
            
            // 2. Validate parameters: Offset must be <= current filesize (no sparse files)
            if (offset < 0 || offset > fileEntry.getFilesize()) {
                throw new Exception("ERROR: Invalid offset for writing (offset must be <= filesize)");
            }

            // 3. Find the starting FNode and position
            int bytesToWrite = data.length;
            int blocksToSkip = offset / BLOCK_SIZE;
            int startBlockOffset = offset % BLOCK_SIZE;
            int currentFNodeIndex = fileEntry.getFirstBlock();
            
            // Traverse to the correct starting FNode.
            for (int i = 0; i < blocksToSkip; i++) {
                if (currentFNodeIndex == -1) {
                    throw new Exception("ERROR: File corruption during write offset calculation.");
                }
                currentFNodeIndex = fnodeTable[currentFNodeIndex].getNext();
            }

            // 4. Handle initial empty file state (if writing to offset 0 of an empty file)
            // The FNode needs its blockIndex set from -1 to an allocated data block.
            if (fileEntry.getFilesize() == 0 && offset == 0) {
                int dataBlockIndex = findFreeDataBlock();
                if (dataBlockIndex == -1) {
                    throw new Exception("ERROR: Not enough disk space to allocate first block.");
                }
                // Update the FNode already allocated for the empty file
                fnodeTable[currentFNodeIndex].setBlockIndex(dataBlockIndex); 
            }

            int dataBufferIndex = 0; // Tracks position in the input 'data' byte array
            int lastFNodeIndex = (blocksToSkip > 0) ? currentFNodeIndex : -1; // Keep track of the node *before* the current one if we traversed

            // 5. Write block by block
            while (bytesToWrite > 0) {
                
                // A. Handle File Extension / Allocation
                if (currentFNodeIndex == -1) {
                    // File needs to be extended: allocate new FNode and Data Block
                    int newFNodeIndex = findFreeFNode();
                    int newDataBlockIndex = findFreeDataBlock(); // This call also marks the block as used

                    if (newFNodeIndex == -1 || newDataBlockIndex == -1) {
                        // Crucial: Handle failure *before* any disk writes (though we don't have disk writes yet)
                        throw new Exception("ERROR: Not enough resources (FNode or Data Block) to extend file.");
                    }

                    // Link the previous FNode to the new FNode
                    if (lastFNodeIndex != -1) {
                        fnodeTable[lastFNodeIndex].setNext(newFNodeIndex);
                    } 
                    
                    // Initialize the new FNode
                    fnodeTable[newFNodeIndex].setBlockIndex(newDataBlockIndex);
                    fnodeTable[newFNodeIndex].setNext(-1); // Mark as the new end of the chain
                    
                    currentFNodeIndex = newFNodeIndex;
                    startBlockOffset = 0; // New blocks always start writing from offset 0
                }
                
                // Get the current FNode and its data block
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                int dataBlockIndex = currentFNode.getBlockIndex();
                
                // B. Calculate Write Size
                int bytesInCurrentBlock = BLOCK_SIZE - startBlockOffset;
                int writeSize = Math.min(bytesToWrite, bytesInCurrentBlock);
                
                // C. DISK I/O LOGIC (Placeholder: You must implement actual disk.write/seek)
                
                byte[] writeData = new byte[writeSize];
                System.arraycopy(data, dataBufferIndex, writeData, 0, writeSize);
                
                // // TODO: Implement actual disk I/O using RandomAccessFile 'disk'
                // disk.seek(getDiskBlockOffset(dataBlockIndex) + startBlockOffset);
                // disk.write(writeData, 0, writeSize);
                
                // Simulate I/O for now:
                System.out.println("DEBUG: Writing " + writeSize + " bytes to block " + dataBlockIndex + " (Disk I/O placeholder).");
                
                // --- END DISK I/O ---
                
                // D. Update Tracking and Move
                
                bytesToWrite -= writeSize;
                dataBufferIndex += writeSize;
                lastFNodeIndex = currentFNodeIndex;
                startBlockOffset = 0; // Reset offset for subsequent blocks
                currentFNodeIndex = currentFNode.getNext();
            }

            // 6. Update Metadata (filesize and persist)
            int newFilesize = offset + data.length;
            if (newFilesize > fileEntry.getFilesize()) {
                fileEntry.setFilesize((short) newFilesize);
            }
            
            // TODO: Call private method to persist fentryTable, fnodeTable, and freeBlockList to 'disk'
            
            System.out.println("SUCCESS: Wrote " + data.length + " bytes to file '" + filename + "'. New size: " + fileEntry.getFilesize() + " bytes.");

        } finally {
            globalLock.unlock();
        }
    }


    // TODO: Add readFile, writeFile, deleteFile, listFiles and other required methods,
}
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


    // TODO: Add readFile, writeFile, deleteFile, listFiles and other required methods,
}
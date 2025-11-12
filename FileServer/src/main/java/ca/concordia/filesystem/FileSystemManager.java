package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    // --- CORE FILE SYSTEM CONSTANTS (Defined as public static final) ---
    public static final int MAXFILES = 5;       // Maximum number of file entries
    public static final int MAXBLOCKS = 10;     // Maximum number of data blocks/fnodes
    public static final int BLOCK_SIZE = 128;   // Size of one data block in bytes

    // --- METADATA STRUCTURE SIZES ---
    public static final int FENTRY_SIZE = 15; // 11 bytes name + 2 bytes size + 2 bytes firstBlock
    public static final int FNODE_SIZE = 4;   // 2 bytes blockIndex + 2 bytes nextBlock

    // --- CALCULATED OFFSETS ---
    // Total Metadata Size: (15 * 5) + (4 * 10) = 115 bytes
    // Data starts at 128 (next multiple of BLOCK_SIZE)
    public static final long DATA_START_OFFSET = (
        (long) Math.ceil((double) (FENTRY_SIZE * MAXFILES + FNODE_SIZE * MAXBLOCKS) / BLOCK_SIZE) * BLOCK_SIZE
    ); 
    
    // Total simulated file system size needed to hold all metadata and data blocks
    public static final long TOTAL_SYSTEM_SIZE = DATA_START_OFFSET + (long) MAXBLOCKS * BLOCK_SIZE;


    // --- INSTANCE FIELDS ---
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();
    
    // In-memory free list for quick access (optional for this step, but good design)
    private boolean[] freeBlockList; 


    public FileSystemManager(String filename) throws Exception {
        if(instance == null) {
            // 1. Initialize RandomAccessFile
            this.disk = new RandomAccessFile(filename, "rw");
            
            // 2. Initialize the file system (Step 4 logic)
            initializeFileSystem(filename);
            
            // 3. Mark the instance
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
    }
    
    // --- SINGLETON ACCESS ---
    public static FileSystemManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FileSystemManager must be initialized via constructor first.");
        }
        return instance;
    }


    /**
     * Step 4: Initializes the file system if the file is newly created or empty.
     */
    private void initializeFileSystem(String filename) throws IOException {
        // If the file is smaller than the required total size, it needs initialization
        if (disk.length() < TOTAL_SYSTEM_SIZE) {
            System.out.println("Initializing new file system: " + filename);
            
            // 1. Clear all FEntries (Mark as unused/empty)
            FEntry emptyEntry = new FEntry();
            for (int i = 0; i < MAXFILES; i++) {
                writeFEntry(i, emptyEntry);
            }

            // 2. Clear all FNODES (Mark all data blocks as free)
            FNode freeNode = new FNode(); // blockIndex and nextBlock are -1 by default
            for (int i = 0; i < MAXBLOCKS; i++) {
                writeFNode(i, freeNode);
            }
            
            // 3. Ensure the file is the correct size by writing zeros up to the end
            // This is crucial for properly setting up the data block area and future seeks
            disk.setLength(TOTAL_SYSTEM_SIZE);
            
            // Optional: Initialize in-memory free list (All are initially free)
            freeBlockList = new boolean[MAXBLOCKS];
            Arrays.fill(freeBlockList, true); 
        } else {
            // If the file exists and is the correct size, you would load the metadata here
            System.out.println("Loading existing file system: " + filename);
            // Optional: Load free list from FNodes on disk here
        }
    }


    // ----------------------------------------------------------------------
    // --- STEP 3.2: BINARY I/O PRIMITIVE METHODS 
    // ----------------------------------------------------------------------

    public FEntry readFEntry(int index) throws IOException {
        long offset = (long) index * FENTRY_SIZE;
        disk.seek(offset);

        FEntry entry = new FEntry();

        // 1. Read the 11-byte filename
        byte[] nameBytes = new byte[FENTRY_SIZE - 4]; // 11 bytes
        disk.readFully(nameBytes);
        entry.setFilenameFromBytes(nameBytes); 

        // 2. Read the 2-byte size
        entry.setFilesize(disk.readShort());

        // 3. Read the 2-byte firstBlock index
        entry.setFirstBlock(disk.readShort());

        return entry;
    }

    public void writeFEntry(int index, FEntry entry) throws IOException {
        long offset = (long) index * FENTRY_SIZE;
        disk.seek(offset);

        // 1. Write the 11-byte filename (Padded/truncated bytes)
        disk.write(entry.getFileNameBytes()); 

        // 2. Write the 2-byte size
        disk.writeShort(entry.getFilesize());

        // 3. Write the 2-byte firstBlock index
        disk.writeShort(entry.getFirstBlock());
    }

    public FNode readFNode(int index) throws IOException {
        // FNodes start after all FEntries
        long offset = (long) MAXFILES * FENTRY_SIZE + (long) index * FNODE_SIZE;
        disk.seek(offset);
        
        // 1. Read the 2-byte blockIndex
        short blockIndex = disk.readShort();
        
        // 2. Read the 2-byte nextBlock index
        short nextBlock = disk.readShort();

        return new FNode(blockIndex, nextBlock);
    }

    public void writeFNode(int index, FNode node) throws IOException {
        // FNodes start after all FEntries
        long offset = (long) MAXFILES * FENTRY_SIZE + (long) index * FNODE_SIZE;
        disk.seek(offset);

        // 1. Write the 2-byte blockIndex
        disk.writeShort(node.getBlockIndex());

        // 2. Write the 2-byte nextBlock index
        disk.writeShort(node.getNextBlock());
    }

    // ----------------------------------------------------------------------
    // --- ASSIGNMENT COMMAND STUBS 
    // ----------------------------------------------------------------------

        public void createFile(String fileName) throws Exception {
        globalLock.lock(); // 1. Acquire Lock
        try {
            // 2. Validate Filename
            if (fileName == null || fileName.isEmpty() || fileName.length() > FEntry.FILENAME_MAX_LENGTH) {
                throw new IllegalArgumentException("Invalid filename: must be 1-11 characters long.");
            }

            int freeEntryIndex = -1;

            // 3. Search for Duplicates & Free Entry
            for (int i = 0; i < MAXFILES; i++) {
                FEntry entry = readFEntry(i);

                // Check for duplicate
                if (entry.getFilename().equals(fileName)) {
                    throw new Exception("File '" + fileName + "' already exists.");
                }

                // Find first free entry (where filename is empty)
                if (entry.getFilename().isEmpty() && freeEntryIndex == -1) {
                    freeEntryIndex = i;
                }
            }

            // 4. Handle Full File System
            if (freeEntryIndex == -1) {
                throw new Exception("File system is full. Cannot create new file.");
            }

            // 5. Initialize and Write New Entry
            // Use the constructor for a new entry: filename, size=0, firstBlock=-1
            FEntry newEntry = new FEntry(fileName, (short) 0, (short) -1);
            
            // Write the new entry to the disk
            writeFEntry(freeEntryIndex, newEntry);
            
            System.out.println("File created successfully at index " + freeEntryIndex + ": " + fileName);

        } finally {
            globalLock.unlock(); // 6. Release Lock
        }
    }

    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("Filename cannot be empty.");
            }

            int fileIndex = -1;
            FEntry fileEntry = null;

            // 1. Find the File Entry
            for (int i = 0; i < MAXFILES; i++) {
                FEntry entry = readFEntry(i);
                if (entry.getFilename().equals(fileName)) {
                    fileIndex = i;
                    fileEntry = entry;
                    break;
                }
            }

            if (fileIndex == -1) {
                throw new Exception("File '" + fileName + "' not found.");
            }

            // 2. Free Data Blocks (Iterate the FNode linked list)
            short currentFNodeIndex = fileEntry.getFirstBlock();
            
            while (currentFNodeIndex != -1) {
                // Read the current FNode
                FNode currentNode = readFNode(currentFNodeIndex);
                
                // Get the index of the actual data block it points to
                short dataBlockIndex = currentNode.getBlockIndex();
                
                // Store the index of the next FNode before we overwrite the current one
                short nextFNodeIndex = currentNode.getNextBlock();

                // A. Mark the associated data block as free
                if (dataBlockIndex != -1) {
                    // Assuming we use the data block index itself (0 to MAXBLOCKS-1) 
                    // to manage the free list.
                    if (dataBlockIndex >= 0 && dataBlockIndex < MAXBLOCKS) {
                        freeBlockList[dataBlockIndex] = true; 
                    }
                }

                // B. Reset the current FNode on disk (Mark it as free/unused)
                writeFNode(currentFNodeIndex, new FNode()); // FNode() resets blockIndex and nextBlock to -1

                // Move to the next FNode in the chain
                currentFNodeIndex = nextFNodeIndex;
            }

            // 3. Free File Entry (Mark the FEntry as unused)
            writeFEntry(fileIndex, new FEntry()); // FEntry() creates an entry with empty name, size=0, firstBlock=-1

            System.out.println("File deleted successfully: " + fileName);

        } finally {
            globalLock.unlock();
        }
    }

    /**
     * Finds the index of the first available (unused) FNode in the metadata by reading the disk.
     * @return The index of the free FNode (0 to MAXBLOCKS-1), or -1 if none are available.
     */
    private short findFreeFNodeIndex() throws IOException {
        // FNode is free if its blockIndex is -1 (default constructor value)
        for (short i = 0; i < MAXBLOCKS; i++) {
            FNode node = readFNode(i);
            if (node.getBlockIndex() == -1) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the index of the first available data block using the in-memory free list (bitmap).
     * @return The index of the free data block (0 to MAXBLOCKS-1), or -1 if none are available.
     */
    private short findFreeDataBlockIndex() {
        for (short i = 0; i < MAXBLOCKS; i++) {
            if (freeBlockList[i]) { // 'true' means the block is free
                return i;
            }
        }
        return -1;
    }
        public void writeFile(String fileName, byte[] data) throws Exception {
        globalLock.lock();
        try {
            // 1. Find File Entry
            int fileIndex = -1;
            FEntry fileEntry = null;
            for (int i = 0; i < MAXFILES; i++) {
                FEntry entry = readFEntry(i);
                if (entry.getFilename().equals(fileName)) {
                    fileIndex = i;
                    fileEntry = entry;
                    break;
                }
            }
            if (fileIndex == -1) {
                throw new Exception("File '" + fileName + "' not found. Must create it first.");
            }

            // --- Handle Old Blocks (Clean up existing data chain before writing new data) ---
            short currentFNodeIndex = fileEntry.getFirstBlock();
            short nextFNodeIndex;
            while (currentFNodeIndex != -1) {
                FNode node = readFNode(currentFNodeIndex);
                
                if (node.getBlockIndex() != -1) {
                    // Free the old data block in the in-memory list
                    freeBlockList[node.getBlockIndex()] = true; 
                }
                
                // Get next index and clear the old FNode on disk
                nextFNodeIndex = node.getNextBlock();
                writeFNode(currentFNodeIndex, new FNode()); // Resets the FNode to free
                currentFNodeIndex = nextFNodeIndex;
            }
            
            // Reset file entry for the new write
            fileEntry.setFirstBlock((short) -1);
            fileEntry.setFilesize((short) 0);
            
            if (data.length == 0) {
                // Write updated FEntry (empty file) and exit
                writeFEntry(fileIndex, fileEntry);
                return;
            }


            // --- New Block Allocation and Writing ---

            int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);
            short lastFNodeIndex = -1; 
            
            for (int i = 0; i < blocksNeeded; i++) {
                short freeDataBlockIndex = findFreeDataBlockIndex();
                short freeFNodeIndex = findFreeFNodeIndex();

                // Check for capacity limitations
                if (freeDataBlockIndex == -1 || freeFNodeIndex == -1) {
                    // Not enough space: partial write failure - you would need to roll back
                    throw new Exception("Out of space. Only " + i + " blocks written.");
                }

                // A. Prepare Data Chunk and Write to Data Block
                int start = i * BLOCK_SIZE;
                int length = Math.min(BLOCK_SIZE, data.length - start);
                byte[] blockData = new byte[length];
                System.arraycopy(data, start, blockData, 0, length);
                
                // Calculate disk offset for the data block
                long dataBlockOffset = DATA_START_OFFSET + (long) freeDataBlockIndex * BLOCK_SIZE;
                
                disk.seek(dataBlockOffset);
                disk.write(blockData);


                // B. Update FNode Metadata
                FNode newFNode = new FNode(freeDataBlockIndex, (short) -1); // Block points to data, next is currently -1
                writeFNode(freeFNodeIndex, newFNode); // Write the new FNode to disk
                
                // Mark the data block as used
                freeBlockList[freeDataBlockIndex] = false; 

                // C. Update the Linked List Pointers
                if (i == 0) {
                    // First block: Update the FEntry
                    fileEntry.setFirstBlock(freeFNodeIndex);
                } else {
                    // Subsequent block: Update the 'nextBlock' pointer of the *previous* FNode
                    FNode previousFNode = readFNode(lastFNodeIndex); // Read the previously written FNode
                    previousFNode.setNextBlock(freeFNodeIndex);
                    writeFNode(lastFNodeIndex, previousFNode);      // Write the updated FNode back
                }
                
                // Update lastFNodeIndex for the next iteration
                lastFNodeIndex = freeFNodeIndex;
            }

            // 3. Final Metadata Update (FEntry)
            fileEntry.setFilesize((short) data.length);
            writeFEntry(fileIndex, fileEntry); // Write final FEntry back to disk

            System.out.println("File written successfully: " + fileName + ". Size: " + data.length + " bytes.");

        } finally {
            globalLock.unlock();
        }
    }
        
    // TODO: Add deleteFile, writeFile, readFile, listFiles, and the server logic here.
    
    // ... rest of the class
}
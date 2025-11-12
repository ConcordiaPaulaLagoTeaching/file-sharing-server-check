package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance = null; 
    private RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();
    
    // Constants
    private static final int FIRST_DATA_BLOCK_INDEX = 1;
    private static final int BLOCK_SIZE = 128; // Example block size

    // File System Metadata (In-Memory Arrays)
    private FEntry[] fentryTable; 
    private FNode[] fnodeTable; 
    private boolean[] freeBlockList;
    
    public FileSystemManager(String filename, int totalSize) throws Exception { 
        if(instance == null) {
            this.fentryTable = new FEntry[MAXFILES];
            this.fnodeTable = new FNode[MAXBLOCKS]; 
            this.freeBlockList = new boolean[MAXBLOCKS];

            for (int i = 0; i < MAXBLOCKS; i++) {
                this.fnodeTable[i] = new FNode(-1, -1); 
            }
            
            this.freeBlockList[0] = true; 
            
            this.disk = new RandomAccessFile(filename, "rw");
            
            if (this.disk.length() < (long) MAXBLOCKS * BLOCK_SIZE) {
                this.disk.setLength((long) MAXBLOCKS * BLOCK_SIZE);
            }
            
            persistMetadata();
            
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
    }
    
    public void createFile(String filename) throws Exception {
        if (filename.length() > 11) { 
            throw new Exception("ERROR: filename too large");
        }

        globalLock.lock();
        try {
            for (FEntry entry : fentryTable) {
                if (entry != null && entry.getFilename().equals(filename)) {
                    throw new Exception("ERROR: file already exists");
                }
            }
            
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

            int fnodeIndex = -1;
            for (int i = 0; i < fnodeTable.length; i++) {
                if (fnodeTable[i].getBlockIndex() < 0) { 
                    fnodeIndex = i;
                    break;
                }
            }
            if (fnodeIndex == -1) {
                throw new Exception("ERROR: No free file nodes available");
            }
            
            fnodeTable[fnodeIndex].setBlockIndex(-1); 
            fnodeTable[fnodeIndex].setNext(-1); 

            fentryTable[fentryIndex] = new FEntry(filename, (short) 0, (short) fnodeIndex);

            persistMetadata();
            
            System.out.println("SUCCESS: Created file '" + filename + "' at FEntry[" + fentryIndex + "] with FNode[" + fnodeIndex + "]");

        } finally {
            globalLock.unlock(); 
        }
    }

    public void deleteFile(String filename) throws Exception {
        
        globalLock.lock(); 
        try {
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

            int currentFNodeIndex = fileEntry.getFirstBlock();
            
            while (currentFNodeIndex != -1) {
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                
                int nextFNodeIndex = currentFNode.getNext();
                int dataBlockIndex = currentFNode.getBlockIndex();

                if (dataBlockIndex >= FIRST_DATA_BLOCK_INDEX && dataBlockIndex < MAXBLOCKS) {
                    freeBlockList[dataBlockIndex] = false; 
                }
                
                currentFNode.setBlockIndex(-1);
                currentFNode.setNext(-1); 
                
                currentFNodeIndex = nextFNodeIndex;
            }

            fentryTable[fentryIndex] = null; 
            persistMetadata();
            
            System.out.println("SUCCESS: Deleted file '" + filename + "' from FEntry[" + fentryIndex + "]");

        } finally {
            globalLock.unlock(); 
        }
    }

    public String[] listFiles() {
        globalLock.lock();
        try {
            int count = 0;
            for (FEntry entry : fentryTable) {
                if (entry != null) {
                    count++;
                }
            }
            
            String[] fileNames = new String[count];
            int index = 0;
            
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

    public byte[] read(String filename, int length, int offset) throws Exception {
        
        globalLock.lock();
        try {
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
            
            if (offset < 0 || offset > fileEntry.getFilesize()) {
                throw new Exception("ERROR: Invalid offset");
            }
            
            int actualReadLength = Math.min(length, fileEntry.getFilesize() - offset);
            
            if (actualReadLength <= 0) {
                return new byte[0]; 
            }
            
            byte[] resultBuffer = new byte[actualReadLength];
            int currentReadOffset = 0;
            
            int blocksToSkip = offset / BLOCK_SIZE;
            int startBlockOffset = offset % BLOCK_SIZE;
            int currentFNodeIndex = fileEntry.getFirstBlock();

            if (fnodeTable[currentFNodeIndex].getBlockIndex() < 0 && blocksToSkip == 0) {
                 return new byte[0]; 
            }
            
            for (int i = 0; i < blocksToSkip; i++) {
                if (currentFNodeIndex == -1) {
                    throw new Exception("ERROR: File is shorter than reported filesize/offset"); 
                }
                currentFNodeIndex = fnodeTable[currentFNodeIndex].getNext();
            }

            int bytesRemainingToRead = actualReadLength;
            
            while (bytesRemainingToRead > 0 && currentFNodeIndex != -1) {
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                int dataBlockIndex = currentFNode.getBlockIndex();
                
                if (dataBlockIndex < FIRST_DATA_BLOCK_INDEX) {
                    throw new Exception("ERROR: FNode chain corruption detected while reading"); 
                }
                
                int bytesInCurrentBlock = BLOCK_SIZE - startBlockOffset;
                int readSize = Math.min(bytesRemainingToRead, bytesInCurrentBlock);
                
                byte[] blockData = new byte[readSize]; 
                
                long position = getDiskBlockOffset(dataBlockIndex) + startBlockOffset;
                disk.seek(position);

                int bytesRead = disk.read(blockData, 0, readSize);
                
                if (bytesRead == -1) {
                    bytesRead = 0;
                }
                
                System.arraycopy(blockData, 0, resultBuffer, currentReadOffset, bytesRead);
                
                bytesRemainingToRead -= bytesRead;
                currentReadOffset += bytesRead;
                startBlockOffset = 0; 
                
                if (bytesRead < readSize) {
                    currentFNodeIndex = -1;
                } else {
                    currentFNodeIndex = currentFNode.getNext();
                }
            }
            
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

    public void write(String filename, byte[] data, int offset) throws Exception {
        
        if (data.length == 0) return;

        globalLock.lock();
        try {
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
            
            if (offset < 0 || offset > fileEntry.getFilesize()) {
                throw new Exception("ERROR: Invalid offset for writing (offset must be <= filesize)");
            }

            int bytesToWrite = data.length;
            int blocksToSkip = offset / BLOCK_SIZE;
            int startBlockOffset = offset % BLOCK_SIZE;
            int currentFNodeIndex = fileEntry.getFirstBlock();
            
            for (int i = 0; i < blocksToSkip; i++) {
                if (currentFNodeIndex == -1) {
                    throw new Exception("ERROR: File corruption during write offset calculation.");
                }
                currentFNodeIndex = fnodeTable[currentFNodeIndex].getNext();
            }

            if (fileEntry.getFilesize() == 0 && offset == 0) {
                int dataBlockIndex = findFreeDataBlock();
                if (dataBlockIndex == -1) {
                    throw new Exception("ERROR: Not enough disk space to allocate first block.");
                }
                fnodeTable[currentFNodeIndex].setBlockIndex(dataBlockIndex); 
            }

            int dataBufferIndex = 0;
            int lastFNodeIndex = (blocksToSkip > 0) ? currentFNodeIndex : -1;

            while (bytesToWrite > 0) {
                
                if (currentFNodeIndex == -1) {
                    int newFNodeIndex = findFreeFNode();
                    int newDataBlockIndex = findFreeDataBlock();

                    if (newFNodeIndex == -1 || newDataBlockIndex == -1) {
                        throw new Exception("ERROR: Not enough resources (FNode or Data Block) to extend file.");
                    }

                    if (lastFNodeIndex != -1) {
                        fnodeTable[lastFNodeIndex].setNext(newFNodeIndex);
                    } 
                    
                    fnodeTable[newFNodeIndex].setBlockIndex(newDataBlockIndex);
                    fnodeTable[newFNodeIndex].setNext(-1);
                    
                    currentFNodeIndex = newFNodeIndex;
                    startBlockOffset = 0;
                }
                
                FNode currentFNode = fnodeTable[currentFNodeIndex];
                int dataBlockIndex = currentFNode.getBlockIndex();
                
                int bytesInCurrentBlock = BLOCK_SIZE - startBlockOffset;
                int writeSize = Math.min(bytesToWrite, bytesInCurrentBlock);
                
                byte[] writeData = new byte[writeSize];
                System.arraycopy(data, dataBufferIndex, writeData, 0, writeSize);
                
                long position = getDiskBlockOffset(dataBlockIndex) + startBlockOffset;
                disk.seek(position);
                disk.write(writeData, 0, writeSize);
                
                bytesToWrite -= writeSize;
                dataBufferIndex += writeSize;
                lastFNodeIndex = currentFNodeIndex;
                startBlockOffset = 0;
                currentFNodeIndex = currentFNode.getNext();
            }

            int newFilesize = offset + data.length;
            if (newFilesize > fileEntry.getFilesize()) {
                fileEntry.setFilesize((short) newFilesize);
            }
            
            persistMetadata();
            
            System.out.println("SUCCESS: Wrote " + data.length + " bytes to file '" + filename + "'. New size: " + fileEntry.getFilesize() + " bytes.");

        } finally {
            globalLock.unlock();
        }
    }

    private long getDiskBlockOffset(int blockIndex) {
        // Calculates the byte offset on the disk where a specific block starts.
        return (long) blockIndex * BLOCK_SIZE;
    }

    private int findFreeDataBlock() {
        for (int i = FIRST_DATA_BLOCK_INDEX; i < MAXBLOCKS; i++) {
            if (!freeBlockList[i]) {
                freeBlockList[i] = true; 
                return i;
            }
        }
        return -1;
    }

    private int findFreeFNode() {
        for (int i = 0; i < fnodeTable.length; i++) {
            if (fnodeTable[i].getBlockIndex() < 0) {
                return i;
            }
        }
        return -1;
    }

    private void persistMetadata() throws Exception {
        // NOTE: Actual serialization logic for fentryTable, fnodeTable, and freeBlockList to disk.seek(0) goes here.
    }
}
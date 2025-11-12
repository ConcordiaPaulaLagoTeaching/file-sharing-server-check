package ca.concordia.filesystem.datastructures;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class FEntry {

    private String filename;
    private short filesize;
    private short firstBlock; // Pointers to data blocks

    // Constant for the required size of the filename field on disk
    public static final int FILENAME_MAX_LENGTH = 11;

    // --- CONSTRUCTORS ---

    // 1. Default constructor for reading entries from the disk or creating an empty file entry
    public FEntry() {
        this.filename = ""; // Empty filename
        this.filesize = 0;
        this.firstBlock = -1; // -1 typically indicates an unused block
    }

    // 2. Constructor provided by the professor
    public FEntry(String filename, short filesize, short firstblock) throws IllegalArgumentException{
        //Check filename is max 11 bytes long
        if (filename.length() > FILENAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Filename cannot be longer than " + FILENAME_MAX_LENGTH + " characters.");
        }
        this.filename = filename;
        this.filesize = filesize;
        this.firstBlock = firstblock;
    }


    // --- I/O HELPER METHODS (Crucial for RandomAccessFile) ---

    /**
     * Converts the internal filename String into a fixed 11-byte array for writing to disk.
     * Pads with null bytes (ASCII 0) if the name is shorter than 11 characters.
     */
    public byte[] getFileNameBytes() {
        // Use UTF-8, but since it's only 11 characters, it should be fine.
        byte[] rawBytes = this.filename.getBytes(StandardCharsets.UTF_8);
        byte[] fixedBytes = new byte[FILENAME_MAX_LENGTH];
        
        // Copy the filename bytes
        System.arraycopy(rawBytes, 0, fixedBytes, 0, Math.min(rawBytes.length, FILENAME_MAX_LENGTH));
        
        // The remaining bytes in fixedBytes are already initialized to 0 (null byte)
        return fixedBytes;
    }

    /**
     * Sets the internal filename String from a raw 11-byte array read from the disk.
     * It stops reading at the first null byte (0) to remove padding.
     */
    public void setFilenameFromBytes(byte[] rawBytes) {
        int length = 0;
        // Find the first null byte (end of the string)
        while (length < rawBytes.length && rawBytes[length] != 0) {
            length++;
        }
        
        // Convert the non-padded part to a String
        this.filename = new String(rawBytes, 0, length, StandardCharsets.UTF_8);
    }


    // --- GETTERS AND SETTERS (Keep these as provided) ---

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        if (filename.length() > FILENAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Filename cannot be longer than " + FILENAME_MAX_LENGTH + " characters.");
        }
        this.filename = filename;
    }

    public short getFilesize() {
        return filesize;
    }

    public void setFilesize(short filesize) {
        if (filesize < 0) {
            throw new IllegalArgumentException("Filesize cannot be negative.");
        }
        this.filesize = filesize;
    }

    public short getFirstBlock() {
        return firstBlock;
    }

    public void setFirstBlock(short firstBlock) {
        this.firstBlock = firstBlock;
    }
}
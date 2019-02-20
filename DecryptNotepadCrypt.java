/* 
 * NotepadCrypt format decrypter
 * 
 * 
 * This standalone program decrypts files that were encrypted in NotepadCrypt's simple format.
 * 
 * The intent of this program is to provide an independent, robust implementation for handling the file format,
 * in case NotepadCrypt (or that author's own small standalone decrypter) is inaccessible or has errors.
 * 
 * Usage: java DecryptNotepadCrypt InputFile [-m] Passphrase
 * Options:
 *     -m: Use master key (only applicable for files with master key)
 * Examples:
 *     java decryptnotepad myencryptedfile.bin password123
 *     java decryptnotepad myencryptedfile.bin -m masterPass456
 *     (Prints to standard output)
 * 
 * 
 * Copyright (c) 2018 Project Nayuki
 * All rights reserved. Contact Nayuki for licensing.
 * https://www.nayuki.io/page/notepadcrypt-format-decryptor-java
 * 
 * NotepadCrypt resources:
 * - http://www.andromeda.com/people/ddyer/notepad/NotepadCrypt.html
 * - http://www.andromeda.com/people/ddyer/notepad/NotepadCrypt-technotes.html
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static java.lang.Integer.rotateRight;
import org.checkerframework.checker.signedness.qual.*;


public final class DecryptNotepadCrypt {
	
	/*---- Main functions ----*/
	
	public static void main(String[] args) throws IOException {
		// Get arguments
		String passphrase;
		boolean useMasterKey;
		if (args.length == 2) {
			useMasterKey = false;
			passphrase = args[1];
		} else if (args.length == 3 && args[1].equals("-m")) {
			useMasterKey = true;
			passphrase = args[2];
		} else {
			System.err.println("Usage: java DecryptNotepadCrypt InputFile [-m] Passphrase");
			System.err.println("    -m: Use master key (only applicable for files with master key)");
			System.exit(1);
			return;
		}
		File inputFile = new File(args[0]);
		
		// Read, decrypt, write

		/* Signedness checker right now not supports Files in java. This class need to be annotated.
		   Here function readAllBytes returns signed value therefore error occurs, hence suppressed the error. */
		@SuppressWarnings("signedness")
		@Unsigned byte[] data = Files.readAllBytes(inputFile.toPath());
		@Unsigned byte[] plaintext;
		try {
			/* getBytes has return type as signed and we need unsigned */
			@SuppressWarnings("signedness")
			@Unsigned byte[] plain = decryptFileData(data, passphrase.getBytes("US-ASCII"), useMasterKey);
			plaintext = plain;
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
			return;
		}
		/* Did this because System.out.write() was unable to write unsigned plaintext */
		@SuppressWarnings("signedness")
		byte[] plaintext2 = plaintext;
		System.out.write(plaintext2);
	}
	
	
	static @Unsigned byte[] decryptFileData(@Unsigned byte[] fileData, @Unsigned byte[] passphrase, boolean useMasterKey) {
		if (fileData.length == 0)
			return fileData;  // NotepadCrypt produces an empty file when trying to encrypt an empty text file
		
		// Parse file format
		boolean hasMasterKey;
		if (toInt32(fileData, 0) != 0x04030201)
			throw new IllegalArgumentException("Unsupported file format");
		switch (toInt32(fileData, 4)) {
			case 0x01000000:
				hasMasterKey = false;
				break;
			case 0x02000000:
				hasMasterKey = true;
				break;
			default:
				throw new IllegalArgumentException("Unsupported encryption format");
		}
		
		// Decrypt text
		@Unsigned byte[] cipherKey = getSha256Hash(passphrase);
		/* function takes parameter as signed value but here filedata is unsigned 
		   and copyOfRange return type is also signed 
		   therefore error needs to be suppressed.*/
		@SuppressWarnings("signedness")
		@Unsigned byte[] initVec = Arrays.copyOfRange(fileData, 8, 24);
		/* function takes parameter as signed value but here filedata is unsigned therefore needs to be suppressed.*/
		@SuppressWarnings("signedness")
		byte[] ciphertext = Arrays.copyOfRange(fileData, hasMasterKey ? 72 : 24, fileData.length);
		if (useMasterKey) {
			if (!hasMasterKey)
				throw new IllegalArgumentException("Master key mode requested on file data with no master key");
			/* function takes parameter as signed value but here filedata is unsigned 
			   and copyOfRange return type is also signed 
			   therefore error needs to be suppressed.*/
			@SuppressWarnings("signedness")
			@Unsigned byte[] iv = Arrays.copyOfRange(fileData, 24, 40);
			/* copyOfRange return is a signed value therefore it is producing warning 
		       thats why suppressed it. It shows that Arrays class need to be annotated into signedness checker */
			@SuppressWarnings("signedness")
			@Unsigned byte[] fileKey = Arrays.copyOfRange(fileData, 40, 72);
			Aes.decryptCbcMode(fileKey, cipherKey, iv);
			cipherKey = fileKey;
		}
		return decryptWithPadding(ciphertext, cipherKey, initVec);
	}
	
	
	/*---- Utility functions ----*/
	
	private static @Unsigned byte[] decryptWithPadding(byte[] ciphertext, @Unsigned byte[] key, @Unsigned byte[] initVec) {
		// Decrypt message
		if (ciphertext.length % 16 != 0)
			throw new IllegalArgumentException("Invalid file length");
		/* clone is function of array class which signedness checker not supports right now
		   therefore it is producing warning hence suppressed it */
		@SuppressWarnings("signedness")
		@Unsigned byte[] plaintext = ciphertext.clone();
		Aes.decryptCbcMode(plaintext, key, initVec);
		
		// Check padding (rejections are always correct, but false acceptance has 1/255 chance)
		int padding = plaintext[plaintext.length - 1];
		if (padding < 1 || padding > 16)
			throw new IllegalArgumentException("Incorrect key or corrupt data");
		for (int i = 1; i <= padding; i++) {
			if (plaintext[plaintext.length - i] != padding)
				throw new IllegalArgumentException("Incorrect key or corrupt data");
		}
		
		// Strip padding
		/* Arrays class is not annotated therefore copyOfRange function requires Signed parameter
		   but here plaintext needs to be unsigned ,and also Arrays.copyOfRange returns signed value which needs to be unsigned 
		   therefore suppressed it. */
		@SuppressWarnings("signedness")
		@Unsigned byte range[]=Arrays.copyOfRange(plaintext, 0, plaintext.length - padding);
		return range;
	}
	
	
	static @Unsigned int toInt32(@Unsigned byte[] b, int off) {  // Big endian
		return (b[off + 0] & 0xFF) << 24 |
		       (b[off + 1] & 0xFF) << 16 |
		       (b[off + 2] & 0xFF) <<  8 |
		       (b[off + 3] & 0xFF) <<  0;
	}
	
	
	/*---- Cryptography library functions ----*/
	
	private static @Unsigned byte[] getSha256Hash(@Unsigned byte[] msg) {
        int len=msg.length;
		if (len > Integer.MAX_VALUE / 8)
			throw new IllegalArgumentException("Message too large for this implementation");
		
		// Add 1 byte for termination, 8 bytes for length, then round up to multiple of block size (64)

		/* return type of copyOf is signed and it takes parameter also as signed
		   but here msg is unsigned therefore producing error hence suppressed. */
		@SuppressWarnings("signedness")
		@Unsigned byte[] padded = Arrays.copyOf(msg, (len+ 1 + 8 + 63) / 64 * 64);
		/* "@IntVal(128) int" may not be casted to the type "@IntVal(-128) byte" warning is suppressed*/
		@SuppressWarnings("value")
		byte q=(byte)0x80;
		padded[msg.length] = q;
		for (int i = 0; i < 4; i++)
			padded[padded.length - 1 - i] = (byte)((len * 8) >>> (i * 8));
		
		// Table of round constants
		final int[] K = {
			0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
			0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
			0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
			0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
			0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
			0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
			0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
			0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
		};
		
		// Compress each block

		@Unsigned int[] state = {0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19};
		for (int off = 0; off < padded.length; off += 64) {
			@Unsigned int[] schedule = new int[64];
			for (int i = 0; i < 16; i++)
				schedule[i] = toInt32(padded, off + i * 4);
			for (int i = 16; i < 64; i++) {
				@Unsigned int x = schedule[i - 15];
				@Unsigned int y = schedule[i -  2];
				/* Unsigned operation is happening on x and y and
				   on the same time rotateRight function is called of Integer class
				   which takes signed number as parameter and
				   right now Signedness checker does not support it, it should be annotated,
				   therefore suppressed the warning. */ 
				@SuppressWarnings("signedness")
				@Unsigned int p = (rotateRight(x,  7) ^ rotateRight(x, 18) ^ (x >>>  3)) +
				                  (rotateRight(y, 17) ^ rotateRight(y, 19) ^ (y >>> 10));
				schedule[i] = schedule[i - 16] + schedule[i - 7] + p;
			}
			
			@Unsigned int a = state[0], b = state[1], c = state[2], d = state[3];
			@Unsigned int e = state[4], f = state[5], g = state[6], h = state[7];
			for (int i = 0; i < 64; i++) {
				/* Same reason as of line no. 211 to 215. */
				@SuppressWarnings("signedness")
				@Unsigned int t1 = h + (rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25)) + ((e & f) ^ (~e & g)) + K[i];
				t1 = t1 + schedule[i];
				/* Same reason as of line no. 211 to 215. */
				@SuppressWarnings("signedness")
				@Unsigned int t2 = (rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22)) + ((a & b) ^ (a & c) ^ (b & c));
				h = g;
				g = f;
				f = e;
				e = d + t1;
				d = c;
				c = b;
				b = a;
				a = t1 + t2;
			}
			state[0] += a; state[1] += b; state[2] += c; state[3] += d;
			state[4] += e; state[5] += f; state[6] += g; state[7] += h;
		}
		
		// Serialize state as result
		@Unsigned byte[] hash = new byte[state.length * 4];
		for (int i = 0; i < hash.length; i++)
			hash[i] = (byte)(state[i / 4] >>> ((3 - i % 4) * 8));
		return hash;
	}
	
}



final class Aes {
	
	private static final int BLOCK_LEN = 16;  // Do not modify
	
	
	public static void decryptCbcMode(@Unsigned byte[] msg, @Unsigned byte[] key, @Unsigned byte[] initVec) {
		if (msg.length % BLOCK_LEN != 0 || initVec.length != BLOCK_LEN)
			throw new IllegalArgumentException("Message is not a multiple of block length");
		
		Aes cipher = new Aes(key);
		@Unsigned byte[] prevCiphertextBlock = initVec;
		for (int off = 0; off < msg.length; off += BLOCK_LEN) {
			/*copyOfRange function requires Signed parameter
			  but here msg needs to be unsigned which arises an error 
			  therefore suppressed it. */
			@SuppressWarnings("signedness")
			@Unsigned byte[] curCiphertextBlock = Arrays.copyOfRange(msg, off, off + BLOCK_LEN);
			cipher.decryptBlock(msg, off);
			for (int i = 0; i < BLOCK_LEN; i++)
				msg[off + i] ^= prevCiphertextBlock[i];
			prevCiphertextBlock = curCiphertextBlock;
		}
	}
	
	
	private @Unsigned byte[][] keySchedule;
	
	
	public Aes(@Unsigned byte[] key) {
		if (key.length < 4 || key.length % 4 != 0)
			throw new IllegalArgumentException("Invalid key length");
		
		// Expand key into key schedule
		int nk = key.length / 4;
		int rounds = Math.max(nk, 4) + 6;
		@Unsigned int[] w = new int[(rounds + 1) * 4];  // Key schedule
		for (int i = 0; i < nk; i++)
			w[i] = DecryptNotepadCrypt.toInt32(key, i * 4);
		@Unsigned byte rcon = 1;
		for (int i = nk; i < w.length; i++) {  // rcon = 2^(i/nk) mod 0x11B
			@Unsigned int tp = w[i - 1];
			if (i % nk == 0) {
				/* rotateRight take signed value as parameter but here tp is unsigned
				   and it returns also signed value which should be unsigned here,
				   therefore suppressed the error.*/
				@SuppressWarnings("signedness")
				@Unsigned int l= subInt32Bytes(rotateRight(tp, 24));
				tp = l ^ (rcon << 24);
				rcon = multiply(rcon, (byte)0x02);
			} else if (nk > 6 && i % nk == 4)
				tp = subInt32Bytes(tp);
			w[i] = w[i - nk] ^ tp;
		}
		
		keySchedule = new @Unsigned byte[w.length / 4][BLOCK_LEN];
		for (int i = 0; i < keySchedule.length; i++) {
			for (int j = 0; j < keySchedule[i].length; j++)
				keySchedule[i][j] = (byte)(w[i * 4 + j / 4] >>> ((3 - j % 4) * 8));
		}
	}
	
	
	public void decryptBlock(@Unsigned byte[] msg, int off) {
		// Initial round
		/* copyOfRange function requires Signed parameter and also returnes signed value,
		   but we need Unsigned here therefore suppressed it */
		@SuppressWarnings("signedness")
		@Unsigned byte[] temp0 = Arrays.copyOfRange(msg, off, off + BLOCK_LEN);
		addRoundKey(temp0, keySchedule[keySchedule.length - 1]);
		@Unsigned byte[] temp1 = new byte[BLOCK_LEN];
		for (int i = 0; i < 4; i++) {  // Shift rows inverse and sub bytes inverse
			for (int j = 0; j < 4; j++)
				temp1[i + j * 4] = SBOX_INVERSE[temp0[i + (j - i + 4) % 4 * 4] & 0xFF];
		}
		
		// Middle rounds
		for (int k = keySchedule.length - 2; k >= 1; k--) {
			addRoundKey(temp1, keySchedule[k]);
			for (int i = 0; i < BLOCK_LEN; i += 4) {  // Mix columns inverse
				for (int j = 0; j < 4; j++) {
					temp0[i + j] = (byte)(
					        multiply(temp1[i + (j + 0) % 4], (byte)0x0E) ^
					        multiply(temp1[i + (j + 1) % 4], (byte)0x0B) ^
					        multiply(temp1[i + (j + 2) % 4], (byte)0x0D) ^
					        multiply(temp1[i + (j + 3) % 4], (byte)0x09));
				}
			}
			for (int i = 0; i < 4; i++) {  // Shift rows inverse and sub bytes inverse
				for (int j = 0; j < 4; j++)
					temp1[i + j * 4] = SBOX_INVERSE[temp0[i + (j - i + 4) % 4 * 4] & 0xFF];
			}
		}
		
		// Final round
		addRoundKey(temp1, keySchedule[0]);
		System.arraycopy(temp1, 0, msg, off, temp1.length);
	}
	
	
	private static void addRoundKey(@Unsigned byte[] block, @Unsigned byte[] key) {
		for (int i = 0; i < BLOCK_LEN; i++)
			block[i] ^= key[i];
	}
	
	
	/* Utilities */
	
	private static @Unsigned byte[] SBOX = new byte[256];
	private static @Unsigned byte[] SBOX_INVERSE = new byte[256];
	
	// Initialize the S-box and inverse
	static {
		for (@Unsigned int i = 0; i < 256; i++) {
			@Unsigned byte tp = reciprocal((byte)i);
			@Unsigned byte s = (byte)(tp ^ rotateByteLeft(tp, 1) ^ rotateByteLeft(tp, 2) ^ rotateByteLeft(tp, 3) ^ rotateByteLeft(tp, 4) ^ 0x63);
			SBOX[i] = s;
			SBOX_INVERSE[s & 0xFF] = (byte)i;
		}
	}
	
	
	private static @Unsigned byte multiply(@Unsigned byte x, @Unsigned byte y) {
		// Russian peasant multiplication
		@Unsigned byte z = 0;
		for (int i = 0; i < 8; i++) {
			z ^= x * ((y >>> i) & 1);
			x = (byte)((x << 1) ^ (((x >>> 7) & 1) * 0x11B));
		}
		return z;
	}
	
	
	private static @Unsigned byte reciprocal(@Unsigned byte x) {
		if (x == 0)
			return 0;
		else {
			for (@Unsigned byte y = 1; y != 0; y++) {
				if (multiply(x, y) == 1)
					return y;
			}
			throw new AssertionError();
		}
	}
	
	
	private static @Unsigned byte rotateByteLeft(@Unsigned byte x, int y) {
		if (y < 0 || y >= 8)
			throw new IllegalArgumentException("Input out of range");
		return (byte)((x << y) | ((x & 0xFF) >>> (8 - y)));
	}
	
	
	private static @Unsigned int subInt32Bytes(@Unsigned int x) {
		return (SBOX[x >>> 24 & 0xFF] & 0xFF) << 24 |
		       (SBOX[x >>> 16 & 0xFF] & 0xFF) << 16 |
		       (SBOX[x >>>  8 & 0xFF] & 0xFF) <<  8 |
		       (SBOX[x >>>  0 & 0xFF] & 0xFF) <<  0;
	}
	
}

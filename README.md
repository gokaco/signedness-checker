**CASE STUDY**:-

This repo contains the Annotated and unannotated version of “NotepadCrypt format decrypter”.

To compile from terminal, following command needs to be passed :

 javac -processor signedness /home/kartik/Desktop/gsoc/DecryptNotepadCrypt.java

By running the command for annotated code- 1 warning and 1 error is occuring:

146: warning : [cast.unsafe] "@IntVal(128) int" may not be casted to the type "@IntVal(-128) byte"
        padded[msg.length] = (byte)0x80;
                             ^

260:error: [assignment.type.incompatible] incompatible types in assignment.
        @Unsigned byte[] temp0 = Arrays.copyOfRange(msg, off, off + BLOCK_LEN);
                                                   ^
  found   : @Signed byte @UnknownSignedness []
  required: @Unsigned byte @UnknownSignedness []
1 error
1 warning

In this case study I learnt that signedness checker does not support classes such as:-

1.)java.util.arrays

2.)java.lang.Integer

3.)java.nio.file.Files

Due to this errors came and in someplaces I had to @SuppressWarnings. I would not have found these problems in signedness checker without doing this case study.
Will further analyze this checker where it needs enhancements.

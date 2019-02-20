**CASE STUDY**:-

This repo contains the Annotated and unannotated version of “NotepadCrypt format decrypter”.

To compile from terminal, following command needs to be passed :

 javac -processor signedness /home/kartik/Desktop/gsoc/DecryptNotepadCrypt.java

In this case study I learnt that signedness checker is weak in some classes and needs enhancements such as:-

1.)java.util.arrays

2.)java.lang.Integer

3.)java.nio.file.Files

and I think many more too.

Due to this errors came and in someplaces I had to @SuppressWarnings. I would not have found these problems in signedness checker without doing this case study.
Will further analyze this checker where it needs more enhancements.

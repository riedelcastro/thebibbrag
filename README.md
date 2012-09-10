The Bib Brag
============

A simple tool for converting your bib file into html pages.

An example output can be seen [here](http://riedelcastro.github.com/publications/all.html). To generate this output
I ran the command

```
java -jar thebibbrag-1.0-SNAPSHOT-standalone.jar -b riedel.bib -t $PWD \\
   -g year --preamble pub.preamble --postamble pub.postamble --authorHomepages homepages.tsv
```

in this [directory](https://github.com/riedelcastro/riedelcastro.github.com/tree/master/publications). Note
that the bibtex file is **not** in the directory. This example shows how `thebibbrag` generates
html for a jekyll site. To this end the pre and post-ample are used to turn the generated output into jekyll pages.

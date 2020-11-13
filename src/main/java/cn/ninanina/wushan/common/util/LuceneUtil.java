package cn.ninanina.wushan.common.util;

import cn.ninanina.wushan.common.Constant;
import lombok.SneakyThrows;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;

public class LuceneUtil {
    private static final LuceneUtil instance = new LuceneUtil();

    private final Analyzer analyzer = new IKAnalyzer();
    private final FSDirectory directory;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;

    @SneakyThrows
    private LuceneUtil(){
        directory = FSDirectory.open(new File(Constant.INDEX_DIR));
    }

    public static LuceneUtil get() {
        return instance;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public FSDirectory getDirectory() {
        return directory;
    }

    public void setIndexReader(IndexReader indexReader) {
        this.indexReader = indexReader;
    }

    public IndexSearcher getIndexSearcher() throws IOException {
        if (indexSearcher == null) {
            indexReader = DirectoryReader.open(directory);
            indexSearcher = new IndexSearcher(indexReader);
        }
        return indexSearcher;
    }

    public void setIndexSearcher(IndexSearcher indexSearcher) {
        this.indexSearcher = indexSearcher;
    }

}

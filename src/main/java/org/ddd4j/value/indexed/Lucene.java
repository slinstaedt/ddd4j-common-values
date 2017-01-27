package org.ddd4j.value.indexed;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.querydsl.core.annotations.QueryEntity;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;

import lombok.Value;

public class Lucene {

	public static void main(String[] args) throws IOException, ParseException {
		Directory directory = buildIndex();
		try (DirectoryReader reader = DirectoryReader.open(directory)) {
			IndexSearcher searcher = new IndexSearcher(reader);
			long started = System.currentTimeMillis();
			// QDocument doc = new QDocument("doc");
			// LuceneQuery query = new LuceneQuery(searcher);
			// List<Document> fetch = query.where(doc.year.between(2002, 2005).and(doc.title.startsWith("Huckle"))).fetch();
			// fetch.forEach(System.out::println);
			BooleanQuery query = new BooleanQuery.Builder().add(IntPoint.newRangeQuery("year", 2002, 2005), Occur.MUST)
					.add(new TermQuery(new Term("title", "Huckle")), Occur.MUST)
					.build();
			TopDocs docs = searcher.search(new TermQuery(new Term("title", "Huckle")), 5);
			System.out.println(docs.totalHits);
			for (ScoreDoc scoreDoc : docs.scoreDocs) {
				Document doc = searcher.doc(scoreDoc.doc);
				System.out.println(doc);
			}
			System.out.println("Queried in " + (System.currentTimeMillis() - started) + " ms");
		}
	}

	static Directory buildIndex() throws IOException {
		// Directory directory = new RAMDirectory();
		// To store an index on disk, use this instead:
		Directory directory = FSDirectory.open(Paths.get("target/index"));
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		try (IndexWriter writer = new IndexWriter(directory, config)) {
			long started = System.currentTimeMillis();
			for (int i = 0; i < 100000; i++) {
				Document doc = new Document();
				UUID id = UUID.randomUUID();
				doc.add(new LongPoint("id", id.getMostSignificantBits(), id.getLeastSignificantBits()));
				doc.add(new StoredField("id-most", id.getMostSignificantBits()));
				doc.add(new StoredField("id-least", id.getLeastSignificantBits()));
				doc.add(new TextField("title", "Huckleberry " + i, Store.YES));
				doc.add(new IntPoint("year", i));
				doc.add(new StoredField("year", i));
				writer.addDocument(doc);
			}
			System.out.println("Imported in " + (System.currentTimeMillis() - started) + " ms");
			// started = System.currentTimeMillis();
			// writer.forceMerge(1);
			// System.out.println("Merged in " + (System.currentTimeMillis() - started) + " ms");
		}
		return directory;
	}
}

@Value
@QueryEntity
class Shipment {

	@Value
	static class Port {

		String name;
	}

	static enum Type {
		SEA, AIR;
	}

	UUID id;
	Type type;
	LocalDateTime eta;
	LocalDateTime ets;
	Port discharge;
	Port arrival;
}

class QDocument extends EntityPathBase<Document> {

	private static final long serialVersionUID = 1L;

	public QDocument(String var) {
		super(Document.class, PathMetadataFactory.forVariable(var));
	}

	public final SimplePath<?> all = createSimple("_all", String.class);

	public final NumberPath<Integer> year = createNumber("year", Integer.class);

	public final StringPath title = createString("title");
}
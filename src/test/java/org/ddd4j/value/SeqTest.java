package org.ddd4j.value;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.List;

import org.ddd4j.value.SeqTest.TplTransformer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.collection.Tpl;
import org.junit.runner.RunWith;

import cucumber.api.Transform;
import cucumber.api.Transformer;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
public class SeqTest {

	public static class TplTransformer extends Transformer<Tpl<String, String>> {

		@Override
		public Tpl<String, String> transform(String value) {
			String[] split = value.split(",");
			return Tpl.of(split[0], split[1]);
		}
	}
}

class Seq2Steps {

	private Seq<String> seq1, seq2;
	private Seq<Tpl<String, String>> result;

	@Given("the sequences \\[(.*)\\] and \\[(.*)\\]")
	public void givenSequences(List<String> s1, List<String> s2) {
		seq1 = s1::stream;
		seq2 = s2::stream;
	}

	@When("inner joined")
	public void whenInnerJoined() {
		result = seq1.join().inner(seq2);
	}

	@Then("the result should be \\[(.*)\\]")
	public void thenCompare(@Transform(TplTransformer.class) List<Tpl<String, String>> r) {
		assertThat(result, equalTo(r));
	}
}

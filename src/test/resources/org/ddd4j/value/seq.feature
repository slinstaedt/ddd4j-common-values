Feature: Joining

	Scenario: Inner joining two sequences
		Given: the sequences [1,2,3,4] and [3,4,5,6]
		When: inner joined
		Then: the result should be [3,4]
		
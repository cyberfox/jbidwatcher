# Created by IntelliJ IDEA.
# User: mrs
# Date: 6/20/15
# Time: 8:43 PM
# To change this template use File | Settings | File Templates.

require 'column_lookup'

describe ColumnLookup do
  let(:lookup) { ColumnLookup.new }

  before :each do
  end

  it "should not be nil" do
    expect(lookup).to_not be_nil
  end
end

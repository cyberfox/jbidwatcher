# Created by IntelliJ IDEA.
# User: mrs
# Date: 6/20/15
# Time: 8:43 PM
# To change this template use File | Settings | File Templates.

java_import com.jbidwatcher.auction.AuctionEntry
java_import java.lang.StringBuffer
require 'column_lookup'
require 'ostruct'

describe ColumnLookup do
  let(:lookup) { ColumnLookup.new }
  let(:entry) { AuctionEntry.new }

  before :each do
    entry.set("identifier", "12345678")
    entry.set("numBids", "1")
    entry.set("currency", "USD")
    entry.set("curBid", "12.50")
  end

  it "should not be nil" do
    expect(lookup).to_not be_nil
  end

  it "should give an identifier when asked for column 0" do
    expect(lookup.get_value(entry, TableColumnController::ID)).to eq("12345678")
  end

  context "current bid" do
    it "should give a sane current bid amount" do
      expect(lookup.get_value(entry, TableColumnController::CUR_BID)).to eq("$12.50 (1)")
    end

    context "fixed price" do
      before :each do
        entry.set("fixed_price", "1")
        entry.set("quantity", "1")
        entry.set("currency", "USD")
        entry.set("curBid", "9.99")
      end

      it "should show (FP)" do
        expect(lookup.get_value(entry, TableColumnController::CUR_BID)).to eq("$9.99 (FP)")
      end

      it "should show (FP x n) when quantity > 1" do
        entry.set("quantity", "2")
        expect(lookup.get_value(entry, TableColumnController::CUR_BID)).to eq("$9.99 (FP x 2)")
      end
    end
  end

  context "max bid" do
    it "should give a simple max bid if one is present" do
      entry.set("last_bid_amount", "14.01")
      expect(lookup.get_value(entry, TableColumnController::MAX)).to eq("$14.01")
    end

    it "should note an error if an error page is present" do
      entry.set("last_bid_amount", "14.01")
      entry.error_page = StringBuffer.new
      expect(lookup.get_value(entry, TableColumnController::MAX)).to eq("*$14.01")
    end

    it "should say 'n/a' if no bid present" do
      expect(lookup.get_value(entry, TableColumnController::MAX)).to eq("n/a")
    end
  end
end

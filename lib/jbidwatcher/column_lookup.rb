# Created by IntelliJ IDEA.
# User: mrs
# Date: 6/20/15
# Time: 4:33 PM
# To change this template use File | Settings | File Templates.

java_import com.jbidwatcher.ui.table.TableColumnController

class ColumnLookup
  LOOKUP_MAP = {
      TableColumnController::ID => proc { |aEntry| aEntry.identifier },
      TableColumnController::CUR_BID => :current_bid.to_proc,
      TableColumnController::SNIPE_OR_MAX => :format_snipe_and_bid.to_proc,
      TableColumnController::MAX => proc { |aEntry| aEntry.isBiOn ? format_bid(aEntry, "*") : "n/a" },
      TableColumnController::SNIPE => proc { |aEntry|},
      TableColumnController::TIME_LEFT => proc { |aEntry|},
      TableColumnController::END_DATE => proc { |aEntry|},
      TableColumnController::TITLE => proc { |aEntry|},
      TableColumnController::STATUS => proc { |aEntry|},
      TableColumnController::THUMBNAIL => proc { |aEntry|},
      TableColumnController::SELLER => proc { |aEntry|},
      TableColumnController::COMMENT => proc { |aEntry|},
      TableColumnController::BIDDER => proc { |aEntry|},
      TableColumnController::FIXED_PRICE => proc { |aEntry|},
      TableColumnController::SHIPPING_INSURANCE => proc { |aEntry|},
      TableColumnController::ITEM_LOCATION => proc { |aEntry|},
      TableColumnController::BIDCOUNT => proc { |aEntry|},
      TableColumnController::JUSTPRICE => proc { |aEntry|},
      TableColumnController::SELLER_FEEDBACK => proc { |aEntry|},
      TableColumnController::SELLER_POSITIVE_FEEDBACK => proc { |aEntry|},
      TableColumnController::CUR_TOTAL => proc { |aEntry|},
      TableColumnController::SNIPE_TOTAL => proc { |aEntry|}
  }

  def current_bid(aEntry)

  end

  def value(col_num, aEntry)
    TableColumnController::I
  end
end

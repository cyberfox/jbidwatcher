require 'java'

java_import com.jbidwatcher.util.config.JConfig
java_import com.cyberfox.util.platform.Path
java_import com.jbidwatcher.util.Currency
java_import com.jbidwatcher.util.Constants
java_import com.jbidwatcher.util.queue.MQFactory
java_import com.jbidwatcher.auction.AuctionEntry
java_import com.jbidwatcher.auction.server.AuctionServerManager
java_import com.jbidwatcher.ui.AuctionsManager
java_import com.jbidwatcher.ui.FilterManager
java_import com.jbidwatcher.ui.table.TableColumnController
java_import com.jbidwatcher.ui.commands.UserActions
java_import com.jbidwatcher.ui.commands.MenuCommand

puts "Loading JBidwatcher Ruby Utilities"

require 'rubygems'
gems = nil

dirname = File.dirname(__FILE__)
if File.exists?(File.join(dirname, 'gems.jar')) || File.exists?('lib/jbidwatcher/gems.jar')
  gems = File.expand_path(File.join(dirname, 'gems.jar'))
else
  gems = JConfig.java_class.class_loader.resource_as_url('lib/jbidwatcher/gems.jar').to_s
end

if File.exists?(File.join(dirname, "gixen")) || File.exists?('lib/jbidwatcher/gixen')
  dirname = File.dirname(__FILE__)
  $:<< File.join(dirname, "gixen")
else
  $:<< JConfig.java_class.class_loader.resource_as_url('lib/jbidwatcher/gixen').to_s
end

ENV['GEM_PATH']="#{gems}!/jruby/1.9"
Gem.paths = ENV
require 'active_support'
require 'active_support/core_ext'
require 'activerecord-jdbcderby-adapter'
require 'digest/md5'
require 'net/http.rb'
require 'cgi'
require 'nokogiri'
require 'json'
require 'open-uri'
require 'ebay_parser'
require 'time'
require 'pp'
require 'gixen'
require 'column_lookup'

class JBidwatcherUtilities
  MY_JBIDWATCHER_URL = "http://my.jbidwatcher.com:9876/advanced"

  def test_basics
    # Check that the basic libraries work.
    puts "This is a test..."
    puts Digest::MD5.hexdigest('foo')

    # Check that accessing objects defined by JBidwatcher works
    c = Currency.getCurrency("$54.98")
    puts c.getValue
    puts c.fullCurrencyName

    puts "Done."
  end

  def about
    MQFactory.getConcrete("user").enqueue("FAQ")
  end

  def fire(user_event)
    MQFactory.getConcrete("user").enqueue(user_event)
  end

  def build_url(meth, hash)
    uri = "#{MY_JBIDWATCHER_URL}/#{meth}"
    url = URI.parse(uri)
    [uri, url, hash.to_param]
  end

  def post(command, hash)
    uri, url, params = build_url(command, hash)

    p = Net::HTTP::Post.new(uri)
    p.body = params
    p.content_type = 'application/x-www-form-urlencoded'

    Net::HTTP.new(url.host, url.port).start do |http|
      http.request p
    end
  end

  def report_exception(exception)
    result = post "exception", {:body => exception}
    puts result.body
    result.body
  end

  def recognize_bidpage(entry, page)
    puts entry.title
    result = post "recognize", {:body => page, :user => JConfig.queryConfiguration("my.jbidwatcher.id")}
    puts result.body
    result.body
  end

  def browse_to(id)
    entry = $auctions_manager.getEntry(id)
    entry.server.showBrowser(entry)
  end

  def notify(message)
    MQFactory.getConcrete("Swing").enqueue("NOTIFY #{message}")
  end

  def snipe(auction, amount)
    entry = auction if(auction.respond_to? :getIdentifier)
    entry ||= $auctions_manager.getEntry(auction)
    amount = Currency.getCurrency(amount) unless amount.respond_to? :fullCurrencyName
    entry.prepareSnipe(amount)
    $filter_manager.redrawEntry(entry)
  end

  def cancel_snipe(id)
    entry = $auctions_manager.getEntry(id)
    entry.cancelSnipe(false)
    $filter_manager.redrawEntry(entry)
  end

  def custom_column(column, auction)
    @columns[column].call(auction).to_s
  end

  def add_column(name, &block)
    $table_controller.add_column(name) unless @columns[name]
    @columns[name] = block
  end

  def initialize
    @columns = {}    
  end

  COMMANDS = {}

  UserActions.java_class.declared_instance_methods.each do |m|
    if m.annotation_present?(MenuCommand.java_class)
      annotation = m.annotation(MenuCommand.java_class)
      COMMANDS[annotation.action] = { :name => m.name.to_sym, :arity => annotation.params } if annotation.action && annotation.action != ""
      COMMANDS[m.name.to_sym] =     { :name => m.name.to_sym, :arity => annotation.params }
    end
  end

  TRANSLATIONS = {
      "Add New" => "Add",
      "Check For Updates" => "Check Updates",
      "Explain Colors And Icons" => "Help Colors"
  }

  def handle_action(action, action_manager, *params)
    do_action = "Do#{action.gsub(' ', '')}".to_sym
    pair = COMMANDS[action] || COMMANDS[do_action]

    method = pair.nil? ? do_action : pair[:name]
    arity = pair.nil? ? 0 : pair[:arity]

    # Special case params: -1 means pass in null as the sole parameter, -2 means just the auction entry.
    params, arity = {-1 => [[nil], 1], -2 => [[params[1]], 1]}[arity] if arity < 0

    if action_manager.respond_to? method
      params = [method] + params[0...arity]
      action_manager.send(*params)
    else
      JConfig.log.logMessage "Did not know how to handle #{action}"
    end
  end

  def load_scripts
    script_dir = Path.getCanonicalFile "scripts","jbidwatcher",false
    if File.exist?(script_dir) && File.directory?(script_dir)
      sd = Dir.new script_dir
      scripts = sd.reject do |filename|
        script_file = File.join(script_dir, filename)
        File.directory?(script_file) || File.extname(script_file) != '.rb'
      end

      scripts.each do |script_file|
        require File.join(script_dir, script_file)
      end
    else
      unless File.exist? script_dir
        Dir.mkdir script_dir
      end
    end
  end

  def after_startup
  end

  def parse(body)
    Ebay::Parser.new(body).parse
  end

  def log(msg)
    JConfig.log.logMessage msg
  end

  def get_update(auction_id, last_updated_at)
    current = (Time.now.to_f*1000).to_i
    last_int = last_updated_at.nil? ? 0 : last_updated_at.time
    lite_url = "http://www.ebay.com/itm/ws/eBayISAPI.dll?ViewItemLite&pbv=0&item=#{auction_id}"
    lite_url += "&lastaccessed=#{last_int}&lvr=0&dl=5&_=#{current}"
    body = open(lite_url).read
    result = JSON.parse(body)
    response = result['ViewItemLiteResponse']

    if response['Error'].empty?
      item = response['Item'].first
      end_date = item['EndDate']

      ends_at = Time.parse(end_date['Time'] + ' ' + end_date['Date'])
      cur_price = item['CurrentPrice']['MoneyStandard']
      ended = item['IsEnded']
      bid_count = item['BidCount']

      {
          'current_price' => cur_price,
          'end_date' => (ends_at.to_f*1000).to_i,
          'ended' => ended,
          'bid_count' => bid_count
      }
    end
  rescue => e
    log e.inspect
    return nil
  end

  def dump_hash(h)
    pp h
  end

  def gixen
    @gixen ||= begin
      server_prefix = Constants.EBAY_SERVER_NAME
      username = JConfig.query_configuration("#{server_prefix}.user")
      password = JConfig.query_configuration("#{server_prefix}.password")
      Gixen.new(username, password) if username != 'default'
    end
  end

  # This is just a dumping ground right now; I should change that soon.
  def get_value(entry, column)

  end
end

# $auction_server_manager = AuctionServerManager.getInstance
# $auctions_manager = AuctionsManager.getInstance
# $filter_manager = $auctions_manager.filters
$table_controller = TableColumnController.getInstance
JBidwatcher = JBidwatcherUtilities.new

JBidwatcher.load_scripts

ActiveRecord::Base.establish_connection(:adapter => 'jdbcderby', :database => "jbdb", username: 'user1', password: 'user1')

class Auction < ActiveRecord::Base
  has_one :entry
  belongs_to :seller
end

# case TableColumnController.CUR_BID :
#     return currentBid(aEntry);
# case TableColumnController.SNIPE_OR_MAX : return formatSnipeAndBid(aEntry);
# case TableColumnController.MAX : return aEntry.isBidOn() ? formatBid(aEntry, errorNote) :neverBid;
# case TableColumnController.SNIPE :
#     return snipeColumn(aEntry, errorNote);
# case TableColumnController.TIME_LEFT :
#     return timeLeftColumn(aEntry);
# case TableColumnController.END_DATE :
#     return endDateColumn(aEntry);
# case TableColumnController.TITLE : return XMLElement.decodeString(aEntry.getTitle());
# case TableColumnController.STATUS : return getEntryIcon(aEntry);
# case TableColumnController.THUMBNAIL :
#     return thumbnailColumn(aEntry);
# case TableColumnController.SELLER :
#     return aEntry.getSellerName();
# case TableColumnController.COMMENT :
#     String comment = aEntry.getComment();
# return(comment==null? "" :comment);
# case TableColumnController.BIDDER :
#     String bidder = aEntry.getHighBidder();
# if (bidder != null && bidder.length() != 0)
#   return bidder;
#   return "--";
#   case TableColumnController.FIXED_PRICE :
#       Currency bin = aEntry.getBuyNow();
#   if (bin.isNull())
#     return "--";
#     return bin;
#     case TableColumnController.SHIPPING_INSURANCE :
#         Currency ship = aEntry.getShippingWithInsurance();
#     if (ship.isNull())
#       return "--";
#       return ship;
#       case TableColumnController.ITEM_LOCATION :
#           return aEntry.getItemLocation();
#       case TableColumnController.BIDCOUNT :
#           if (aEntry.getNumBidders() < 0)
#             return "(FP)";
#             return Integer.toString(aEntry.getNumBidders());
#             case TableColumnController.JUSTPRICE :
#                 return aEntry.getCurrentPrice();
#             case TableColumnController.SELLER_FEEDBACK :
#                 return seller.getFeedback();
#             case TableColumnController.SELLER_POSITIVE_FEEDBACK :
#                 String fbp = seller.getPositivePercentage();
#             return (fbp == null || fbp.length() == 0) ? "--" :fbp;
#             case TableColumnController.CUR_TOTAL :
#                 return priceWithShippingColumn(aEntry);
#             case TableColumnController.SNIPE_TOTAL :
#                 return formatTotalSnipe(aEntry, errorNote);
#
#

class Entry < ActiveRecord::Base
  belongs_to :auction
  belongs_to :multisnipe
  belongs_to :snipe
  belongs_to :category

  class << self
    def instance_method_already_implemented?(method_name)
      return true if method_name == "invalid?"
      super
    end
  end
end

class Seller < ActiveRecord::Base
  has_many :auctions
end

class Category < ActiveRecord::Base
  has_many :entries
end

class Snipe < ActiveRecord::Base
  has_one :entry
end

class Multisnipe < ActiveRecord::Base
  has_many :entries
end

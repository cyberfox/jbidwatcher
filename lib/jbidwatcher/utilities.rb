require 'java'

$: << "META-INF/jruby.home/lib/ruby/1.8"

require 'digest/md5'
require 'net/http.rb'
require 'cgi'

java_import com.jbidwatcher.util.config.JConfig
java_import com.cyberfox.util.platform.Path
java_import com.jbidwatcher.util.Currency
java_import com.jbidwatcher.util.queue.MQFactory
java_import com.jbidwatcher.auction.AuctionEntry
java_import com.jbidwatcher.auction.Category
java_import com.jbidwatcher.auction.server.AuctionServerManager
java_import com.jbidwatcher.ui.AuctionsManager
java_import com.jbidwatcher.ui.FilterManager
java_import com.jbidwatcher.ui.table.TableColumnController

puts "Loading JBidwatcher Ruby Utilities"

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

  def play_around(message)
    puts "This is a message: #{message.reverse}"
  end

  def build_url(meth, hash)
    params = hash.collect {|x,y| "#{CGI.escape(x.to_s)}=#{CGI.escape(y.to_s)}"}.join('&')

    uri = "#{MY_JBIDWATCHER_URL}/#{meth}"
    url = URI.parse(uri)
    [uri, url, params]
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
end

$auction_server_manager = AuctionServerManager.getInstance
$auctions_manager = AuctionsManager.getInstance
$filter_manager = $auctions_manager.filters
$table_controller = TableColumnController.getInstance

JBidwatcher = JBidwatcherUtilities.new

JBidwatcher.load_scripts

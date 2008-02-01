require 'md5'
require 'net/http'
require 'cgi'

import com.jbidwatcher.config.JConfig
import com.jbidwatcher.util.Currency
import com.jbidwatcher.queue.MQFactory
import com.jbidwatcher.auction.AuctionEntry
import com.jbidwatcher.auction.server.AuctionServerManager
import com.jbidwatcher.auction.AuctionsManager
import com.jbidwatcher.FilterManager
import com.jbidwatcher.ui.TableColumnController

class JBidwatcherUtilities
  def test_basics
    # Check that the basic libraries work.
    puts "This is a test..."
    puts MD5.hexdigest('foo')

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

    uri = "http://my.jbidwatcher.com:9876/advanced/#{meth}"
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
end

$auction_server_manager = AuctionServerManager.getInstance
$auctions_manager = AuctionsManager.getInstance
$filter_manager = FilterManager.getInstance
$table_controller = TableColumnController.getInstance

JBidwatcher = JBidwatcherUtilities.new

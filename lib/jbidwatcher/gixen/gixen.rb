#!/usr/bin/env ruby

require 'uri'
require 'cgi'
require 'net/https'

require 'gixen_error'
begin
  require 'to_query'
rescue LoadError => err
  require 'active_support'
  require 'active_support/all'
end

class Gixen
  CORE_GIXEN_URL='https://www.gixen.com/api.php' #:nodoc:

  #:nodoc:
  LISTING_FORMAT = [:break,
                    :itemid,
                    :endtime,
                    :maxbid,
                    :status,
                    :message,
                    :title,
                    :snipegroup,
                    :quantity,
                    :bidoffset]

  # Create a Gixen object for interacting with the user's Gixen
  # account, placing snipes, deleting snipes, and determining what
  # snipes have been set up.
  # 
  # [+user+] an eBay username
  # [+pass+] an eBay password
  # [+validate_ssl+] true requests validation against the gixen cert file, false presumes the SSL is valid.  Defaults to false.
  # 
  # Gixen uses eBay authentication for its users, so it doesn't have
  # to have a different user/pass for its users.
  def initialize(user, pass, validate_ssl = false)
    @username = user
    @password = pass
    @validate_ssl = validate_ssl
  end

  private
  def gixen_url
    "#{CORE_GIXEN_URL}?username=#{@username}&password=#{@password}&notags=1"
  end

  def submit(params)
    url = "#{gixen_url}&#{params.to_query}"
    uri = URI.parse(url)
    http = Net::HTTP.new(uri.host, uri.port)
    http.use_ssl = true if uri.scheme == 'https' # enable SSL/TLS
    if @validate_ssl
      http.verify_mode = OpenSSL::SSL::VERIFY_PEER
      http.ca_file = pem_file
    else
      http.verify_mode = OpenSSL::SSL::VERIFY_NONE
    end
    http.get("#{uri.path}?#{uri.query}")
  end

  def pem_file
    File.expand_path(File.dirname(__FILE__) + "/gixen.pem")
  end

  def parse_response(resp)
    data = resp.body
    data.strip! if data
    if data =~ /^ERROR \(([0-9]+)\): (.*)$/
      error_code = $1
      error_text = $2
      raise GixenError.new(error_code.to_i, error_text)
    end
    data
  end

  def handle_snipes_list(body)
    body.split("\n").inject([]) do |accum, line|
      line.strip!
      hash = {}
      line.split("|#!#|").each_with_index do |entry, index|
        if index < LISTING_FORMAT.length
          hash[LISTING_FORMAT[index]] = entry
        else
          hash[index] = entry
        end
      end unless line =~ %r%^<br.*/>OK M(AIN|IRROR) LISTED$%
      hash.empty? ? accum : (accum << hash)
    end
  end

  def sourcify_hashes(hash_list, mirror = false)
    hash_list.each do |h|
      h[:mirror] = mirror
    end
  end

  public
  # Place a snipe on an +item+ (the auction item #) for +bid+ (string amount).
  # [+item+] the item number of the listing on eBay
  # [+bid+] a string amount of currency-neutral money, for example "23.50". The currency bid in will be the currency of the listing
  # [+options+] A collection of optional parameters for setting snipe meta-data.
  # Optional parameters include:
  # * <tt>:snipegroup => <i>group number</i></tt>, e.g. <tt>:snipegroup => 1</tt> (default: 0, no groups used)
  # * <tt>:quantity => <i>number</i></tt> (default: 1, single item auction) <b>[_obsolete_]</b>
  # * <tt>:bidoffset => <i>seconds before end</i></tt> (3, 6, 8, 10 or 15. Default value is 6)
  # * <tt>:bidoffsetmirror => <i>seconds before end</i></tt> (same as above, just for mirror server)
  #
  # @return [boolean] true if the snipe was successfully set, false if
  # something other than 'OK ADDED' came back, and throws a GixenError
  # if there is any server-side problem.
  def snipe(item, bid, options = {})
    response = submit({:itemid => item, :maxbid => bid}.merge(options))
    body = parse_response(response).strip
    !!(body =~ /^OK #{item} ADDED$/)
  end

  # Remove a snipe from an +item+ (the auction item #).
  #
  # @return [boolean] true if the snipe was successfully deleted, false if
  # something other than 'OK DELETED' came back, and throws a GixenError
  # if there is any server-side problem.
  def unsnipe(item)
    response = submit({:ditemid => item})
    body = parse_response(response).strip
    !!(body =~ /^OK #{item} DELETED$/)
  end

  # Lists all snipes set, skipped, or done on any Gixen server.
  # 
  # @return [array of hashes] an array where each entry is a hash
  # containing all the info for a given snipe, an empty array if no
  # auctions are listed on either the main Gixen server or the Gixen
  # Mirror server.
  # 
  # An additional field is added, :mirror, which is either true or
  # false, depending on if the item hash came from the mirror server
  # or not.
  # 
  # It raises a GixenError if there is a server-side problem.
  def snipes
    sourcify_hashes(main_snipes) + sourcify_hashes(mirror_snipes, true)
  end

  # Lists all snipes currently set set, skipped, or done on Gixen's main server.
  # 
  # @return [array of hashes] an array where each entry is a hash
  # containing all the info for a given snipe, an empty array if none
  # are listed on the main Gixen server.  It raises a GixenError if
  # there is a server-side problem.
  def main_snipes
    response = submit({:listsnipesmain => 1})
    body = parse_response(response)
    handle_snipes_list(body)
  end

  # List all snipes currently set set, skipped, or done on Gixen's mirror server.
  # 
  # @return [array of hashes] an array where each entry is a hash
  # containing all the info for a given snipe, an empty array if none
  # are listed on the Gixen Mirror server.  It raises a GixenError if
  # there is a server-side problem.
  def mirror_snipes
    response = submit({:listsnipesmirror => 1})
    body = parse_response(response)
    handle_snipes_list(body)
  end

  # Normally the snipes that are completed are still listed when
  # retrieving snipes from the server; this method clears completed
  # listings, so the list of snipes is just active snipes.
  #
  # @return [boolean] true if the purge completed successfully.  It
  # raises a GixenError if there is a server-side problem.
  def purge
    response = submit({:purgecompleted => 1})
    body = parse_response(response).strip
    !!(body =~ /^OK COMPLETEDPURGED$/)
  end
end

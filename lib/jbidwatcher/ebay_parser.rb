#
#  ebay_parser.rb
#  MacBidwatcher
#
#  Created by Morgan Schweers on 7/24/11.
#  Copyright 2011 CyberFOX Software, Inc. All rights reserved.
#

require 'parser'

class NilClass; def to_nil; end end
class String; def to_nil; self if !empty? end end
class Array; def to_nil; self unless empty? end end

class Ebay
  class Parser < ::Parser
    PRICE_MATCH=[%r{(Discounted.price|(Current.bid|Winning.bid|Starting.bid|Price|Sold.[Ff]or):)}, %r{.*[0-9].*}]
    LOCATION_MATCH=[%r{(?i)item.location:}, %r{.*}]
    END_DATE_MATCH=[%r{^\(.*$}, %r{^.*(P[SD]T|GMT)\)$}]
    ENDED_DATE_MATCH=[%r{^Ended:$},%r{.*}, %r{.*(P[SD]T|GMT)$}]
    SHIPPING_MATCH=[%r{^Shipping:$}, %r{.*}]
    ITEM_MATCH=[%r{^Item.number:}, %r{[0-9]+}]
    REVERSE_ITEM_MATCH=[%r{[0-9]+}, %r{Item.number:}]
    HISTORY_MATCH=[%r{^Bid.history:$}, %r{[0-9]+}, %r{bids?}]
    ENDED_HISTORY_MATCH=[%r{^Bid.history:$}, %r{[0-9]+.bids?}]
    LIVE_HISTORY_MATCH = PRICE_MATCH + ["\\[", %r{[0-9]+ bids?}]
    BIDCOUNT_MATCH = ["\\[", %r{\d+}, %r{bids?}, "\\]"]
    LIVE_SELLER_MATCH = [%r{Member id}, %r{.*}, %r{\(}, %r{Feedback Score.*}, %r{[0-9]+}, %r{\)}, %r{[0-9.]+.*}]
    ENDED_SELLER_MATCH= ['Seller:'] + LIVE_SELLER_MATCH[0..4]

    def parse_extended_end_date
      end_date = match_set(END_DATE_MATCH)
      if end_date
        ends_at = end_date.join(' ')
        ends_at = ends_at[1..-2]
        else
        ends_at = match_set(ENDED_DATE_MATCH)
        ends_at = ends_at[1..-1].join(' ') if ends_at
      end
      ends_at
    end

    def parse_bid_count
      if bid_history = match_set(HISTORY_MATCH)
        bid_history[1]
      elsif bid_history = match_set(LIVE_HISTORY_MATCH)
        bid_history[3].split.first
      elsif bid_history = match_set(ENDED_HISTORY_MATCH)
        bid_history[1].split.first
      end
    end

    def extract_title(title, title_array)
      title.gsub(title_array[0], '').to_nil || @page.css('meta[property="og:title"]').attribute('content').value
    end

    def extract_id(title_array)
      id_match = match_set(REVERSE_ITEM_MATCH)
      return id_match.first
    end

    def old_extract_id(title_array)
#      return "261116602845"
      id = title_array[1]
      unless id
        id_match = match_set(ITEM_MATCH)
        id = id_match.last if id_match
        unless id
          id_match = match_set(REVERSE_ITEM_MATCH)
          id = id_match.first if id_match
          unless id
            item_id_search = @page.css('span:contains("Item number")').text
            if item_id_search && item_id_search != []
              potential_id = item_id_search.match(%r{Item.number:?.(\d+)})
              id = potential_id[1]
            end
          end
        end
      end
      id
    end

    def parse_title
      title = @page.title
      title_array = title.match(%r{(?: \| eBay)|- eBay \(item ([0-9]+) end time *([^)]+)\)})
      if title_array.nil?
        nil
      else
        title = extract_title(title, title_array)
        id = extract_id(title_array)
        ends_at = title_array[2]
        { :title => title, :id => id, :ends_at => ends_at }
      end
    end

    def extract_thumbnail
      thumbnail_image_set = (@page / 'img').select do |node|
        if node
          node[:src] =~ /ebayimg.com/
        else
          false
        end
      end.to_a.first

      thumbnail_image = thumbnail_image_set[:src] if thumbnail_image_set
      thumbnail_image || @page.css('meta[property="og:image"]').attribute('content').value
    end

    # @return [Array[feedback_percent, feedback_score, seller_name]] The seller-related information; feedback_percent may be nil if the item is ended.
    def extract_seller_info
      seller_info = match_set(LIVE_SELLER_MATCH)
      seller_name, feedback_score, feedback_percent = if seller_info
                                                        [seller_info[1], seller_info[4], seller_info.last.split('%').first]
                                                      end
      unless seller_info
        seller_info = match_set(ENDED_SELLER_MATCH)
        seller_name, feedback_score = if seller_info
                                        [seller_info[2], seller_info[5]]
                                      end
      end
      return feedback_percent, feedback_score, seller_name
    end

    # @return [Hash] Parsed data from the auction as a key/value hash, or nil if the title isn't recognizable.
    def parse
      title_info = parse_title
      return nil unless title_info

      thumbnail_field = {}
      thumbnail_field['thumbnail_url'] = extract_thumbnail

      price = match_set(PRICE_MATCH).to_a.last.to_nil || @page.css('span[itemprop="price"]').text
      location = match_set(LOCATION_MATCH).to_a.last
      shipping = match_set(SHIPPING_MATCH).to_a.last

      ends_at = title_info[:ends_at] || parse_extended_end_date
      bid_count = parse_bid_count

      test_price = @page.css("[itemprop='offers'] .convPrice #bidPrice").text

      unless bid_count
        found = match_set(BIDCOUNT_MATCH)
        bid_count = found[1] if found
      end

      feedback_percent, feedback_score, seller_name = extract_seller_info

      { :title => title_info[:title].to_s.strip,
        :location => location.to_s.strip,
        :current_price => price,
        :us_price => test_price,
        :end_date => ends_at,
        :shipping => shipping,
        :bid_count => bid_count,
        :identifier => title_info[:id].to_s.strip,
        :seller => {
          :seller_name => seller_name.to_s.strip,
          :feedback => feedback_score.to_s.strip,
          :feedback_percent => feedback_percent.to_s.strip
        }
      }.merge(thumbnail_field)
    end
  end
end

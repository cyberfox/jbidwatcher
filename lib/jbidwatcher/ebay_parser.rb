#
#  ebay_parser.rb
#  MacBidwatcher
#
#  Created by Morgan Schweers on 7/24/11.
#  Copyright 2011 CyberFOX Software, Inc. All rights reserved.
#

require 'parser'

class Ebay
  class Parser < ::Parser
    PRICE_MATCH=[%r{(Discounted.price|(Current.bid|Winning.bid|Starting.bid|Price|Sold.[Ff]or):)}, %r{.*[0-9].*}]
    LOCATION_MATCH=[%r{(?i)item.location:}, %r{.*}]
    END_DATE_MATCH=[%r{^\(.*$}, %r{^.*(P[SD]T|GMT)\)$}]
    ENDED_DATE_MATCH=[%r{^Ended:$},%r{.*}, %r{.*(P[SD]T|GMT)$}]
    SHIPPING_MATCH=[%r{^Shipping:$}, %r{.*}]
    ITEM_MATCH=[%r{^Item.number:}, %r{[0-9]+}]
    HISTORY_MATCH=[%r{^Bid.history:$}, %r{[0-9]+}, %r{bids?}]
    ENDED_HISTORY_MATCH=[%r{^Bid.history:$}, %r{[0-9]+.bids?}]
    LIVE_HISTORY_MATCH = PRICE_MATCH + ["\\[", %r{[0-9]+ bids?}]
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

    def parse_title
      title = @page.title
      title_array = title.match(%r{(?: \| eBay)|- eBay \(item ([0-9]+) end time *([^)]+)\)})
      if title_array.nil?
        nil
      else
        title = title.gsub(title_array[0], '')
        id = title_array[1]
        unless id
          id_match = match_set(ITEM_MATCH)
          id = id_match.last if id_match
        end
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

    def parse
      title_info = parse_title
      return nil unless title_info

      thumbnail_field = {}
      thumbnail_field['thumbnail_url'] = extract_thumbnail

      price = match_set(PRICE_MATCH).to_a.last
      location = match_set(LOCATION_MATCH).to_a.last
      shipping = match_set(SHIPPING_MATCH).to_a.last

      ends_at = title_info[:ends_at] || parse_extended_end_date
      bid_count = parse_bid_count

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

      { 'title' => title_info[:title].strip, 'location' => location.strip,
        'current_price' => price, 'end_date' => ends_at,
        'shipping' => shipping, 'bid_count' => bid_count,
        'identifier' => title_info[:id].to_s.strip,
        'seller' => {
          'seller_name' => seller_name.strip,
          'feedback' => feedback_score.strip,
          'feedback_percent' => feedback_percent.strip
        }
      }.merge(thumbnail_field)
    end
  end
end

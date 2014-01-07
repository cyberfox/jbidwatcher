#
#  parser.rb
#  MacBidwatcher
#
#  Created by Morgan Schweers on 7/24/11.
#  Copyright 2011 CyberFOX Software, Inc. All rights reserved.
#

require 'nokogiri'

class Parser
  # @param page The HTML of the page to parse
  def initialize(page)
    @page = Nokogiri::HTML.parse(page)
  end
  
  def match_set(match)
    match_step = 0
    result = []
    @page.root.traverse do |node|
      if node.text?
        if node.text.strip.match(match[match_step])
          result << node.text
          match_step += 1
          return result if match_step == match.length
        elsif !node.text.strip.empty? && !result.empty?
          result.clear
          match_step = 0
        end
      end
    end
    nil
  end
end

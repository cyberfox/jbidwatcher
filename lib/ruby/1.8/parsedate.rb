# parsedate.rb: Written by Tadayoshi Funaba 2001, 2002
# $Id: parsedate.rb 2910 2007-02-02 04:42:15Z headius $

require 'date/format'

module ParseDate

  def parsedate(str, comp=false)
    Date._parse(str, comp).
      values_at(:year, :mon, :mday, :hour, :min, :sec, :zone, :wday)
  end

  module_function :parsedate

end

require 'md5'

import com.jbidwatcher.util.Currency

puts "This is a test..."
puts MD5.hexdigest('foo')

c = Currency.getCurrency("$54.98")
puts c.getValue
puts c.fullCurrencyName

puts "Done."

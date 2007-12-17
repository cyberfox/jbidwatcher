require 'md5'

import com.jbidwatcher.util.Currency
import com.jbidwatcher.queue.MQFactory

puts "This is a test..."
puts MD5.hexdigest('foo')

c = Currency.getCurrency("$54.98")
puts c.getValue
puts c.fullCurrencyName
puts "Done."

def about
  MQFactory.getConcrete("user").enqueue("FAQ")
end

def fire(user_event)
  MQFactory.getConcrete("user").enqueue(user_event)
end

def play_around(message)
  puts "This is a message: #{message.reverse}"
end

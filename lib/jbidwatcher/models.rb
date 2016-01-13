# Created by IntelliJ IDEA.
# User: mrs
# Date: 1/12/16
# Time: 10:46 PM
# To change this template use File | Settings | File Templates.

require 'activerecord-jdbcderby-adapter'

driver = JConfig.query_configuration("db.driver") || "org.apache.derby.jdbc.EmbeddedDriver"

if driver.include? 'mysql'
  db = JConfig.query_configuration("db.mysql.database")
  user = JConfig.query_configuration('db.user')
  pass = JConfig.query_configuration('db.pass')
  protocol = JConfig.query_configuration('db.protocol') # e.g. jdbc:mysql://cyberfox.com:3306/
  ActiveRecord::Base.establish_connection(adapter: 'jdbc', driver: 'com.mysql.jdbc.Driver', url: "#{protocol}#{db}", username: user, password: pass)
else
  ActiveRecord::Base.establish_connection(adapter: 'jdbcderby', database: 'jbdb', username: 'user1', password: 'user1')
end

class Auction < ActiveRecord::Base
  has_one :entry
  belongs_to :seller
end

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

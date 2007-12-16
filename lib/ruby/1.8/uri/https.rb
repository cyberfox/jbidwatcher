#
# = uri/https.rb
#
# Author:: Akira Yamada <akira@ruby-lang.org>
# License:: You can redistribute it and/or modify it under the same term as Ruby.
# Revision:: $Id: https.rb 2062 2006-06-10 19:14:15Z headius $
#

require 'uri/http'

module URI
  class HTTPS < HTTP
    DEFAULT_PORT = 443
  end
  @@schemes['HTTPS'] = HTTPS
end

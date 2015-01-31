class Gixen
  GIXEN_ERROR_MAP = {
    501 => :ssl_required,     # ERROR (501): SSL REQUIRED
    100 => :could_not_log_in, # ERROR (100): COULD NOT LOG IN
    101 => :could_not_log_in, # ERROR (101): COULD NOT LOG IN
    1001 => :down,            # ERROR (1001): GIXEN DOWN
    111 => :not_subscriber,   # ERROR (111): NON SUBSCRIBER
    115 => :suspended,        # ERROR (115): GIXEN ACCOUNT SUSPENDED
    771 => :delete_failed,    # ERROR (771): DELETE QUERY FAILED
    772 => :delete_failed,    # ERROR (772): DELETE QUERY FAILED
    131 => :no_username,      # ERROR (131): USERNAME NOT SPECIFIED
    132 => :no_password,      # ERROR (132): PASSWORD NOT SPECIFIED
    133 => :no_maxbid,        # ERROR (133): MAXIMUM BID NOT SPECIFIED
    201 => :too_many_snipes,  # ERROR (201): MAXIMUM NUMBER OF SNIPES EXCEEDED
    202 => :already_present,  # ERROR (202): ITEM ALREADY PRESENT
    203 => :cannot_get_info,  # ERROR (203): COULD NOT GET INFO FOR ITEM
    301 => :format_invalid,   # ERROR (301): COULD NOT ADD SNIPE: AUCTION NUMBER OR MONEY FORMAT INVALID
    302 => :error_adding_to_mirror, # ERROR (302): COULD NOT ADD SNIPE TO MIRROR
    304 => :error_adding_to_mirror, # ERROR (304): COULD NOT ADD SNIPE TO MIRROR
    401 => :empty_authentication,   # ERROR (401): EMPTY USERNAME OR EMPTY PASSWORD
    241 => :delete_failed,    # ERROR (241): DELETE QUERY FAILED
    242 => :delete_failed     # ERROR (242): DELETE QUERY FAILED
  }

  # GixenError is raised when there's a problem with the response, or
  # the Gixen server has returned an error.
  class GixenError < RuntimeError
    def initialize(code, text)
      super(text)
      @code = code
      @status = GIXEN_ERROR_MAP[@code]
      @message = text
    end

    attr_reader :status, :code, :message

    def to_s
      "#{code} #{status} - #{message}"
    end
  end
end

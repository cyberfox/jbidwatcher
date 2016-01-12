require 'utilities'

describe JBidwatcher do
  it "should test okay" do
    JBidwatcher.test_basics
  end

  it "should handle private mapped actions" do
    class MockUserActions < Hash
      private
      def CancelSnipe(x, y)
        self['cancel_snipe_called'] = true
      end
    end

    m = Hash.new
    def m.logMessage(x)
      self['log_result'] = x
    end
    JConfig.set_logger(m)

    c = "Component" # A mock swing component.
    ae = "Auction Entry" # A mock auction entry

    user_actions = MockUserActions.new
    JBidwatcher.handle_action('Cancel Snipe', user_actions, c, ae)

    # Assert that nothing was logged.
    expect(m['log_result']).to be_nil
    expect(user_actions['cancel_snipe_called']).to be true
  end
end

LUPAPISTE.ApplicationsSearchPagingModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;
  self.limits = params.limits;
  self.count = params.count;

  self.paging = function(direction) {
    var limit = self.dataProvider.limit();
    var skipVal = self.dataProvider.skip();
    var multiplier = limit * direction;
    if ( _.isFinite(multiplier) ) {
      self.dataProvider.skip(skipVal + multiplier);
    }
  };

  self.next = _.partial(self.paging, 1);
  self.prev = _.partial(self.paging, -1);

  self.start = ko.pureComputed(function() {
    return self.dataProvider.skip() + 1;
  });

  self.end = ko.pureComputed(function() {
    var endValue = self.dataProvider.skip() + self.dataProvider.limit();
    return self.count() < endValue ? self.count() : endValue;
  });

  self.limitSubscription = self.dataProvider.limit.subscribe(function() {
    self.dataProvider.skip(0); // reset skip when limits change
  });

  self.dispose = self.limitSubscription;
};

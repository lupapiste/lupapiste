LUPAPISTE.ApplicationsSearchPagingModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;
  self.limits = params.limits;
  self.count = ko.pureComputed(function() {
    return self.dataProvider.data().totalCount;
  });

  self.limit = params.dataProvider.limit;

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

  self.enablePrevButton = ko.pureComputed(function() {
    return self.count() > self.limit() && self.start() > self.limit();
  });

  self.enableNextButton = ko.pureComputed(function() {
    return self.count() > self.limit() && self.count() > self.end();
  });

  self.setLimit = function(value) {
    self.limit(value);
  };
};

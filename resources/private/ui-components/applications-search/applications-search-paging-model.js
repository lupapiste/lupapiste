LUPAPISTE.ApplicationsSearchPagingModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;
  self.limits = params.limits;
  self.count = ko.pureComputed(function() {
    return self.dataProvider.totalCount();
  });

  self.limit = params.dataProvider.limit;
  self.disable = params.dataProvider.disable || ko.observable(false);

  self.paging = function(direction) {
    var limit = self.dataProvider.limit();
    var skipVal = self.dataProvider.skip();
    var multiplier = limit * direction;
    if ( _.isFinite(multiplier) ) {
      self.dataProvider.skip(skipVal + multiplier);
    }
  };

  self.resultsTextKey = params.resultsTextKey || "applications.results";
  self.itemsPerPageTextKey = params.itemsPerPageTextKey || "applications.lengthMenu";

  self.toStartBtn = params.toStartBtn || ko.observable(false);
  self.toEndBtn   = params.toEndBtn   || ko.observable(false);

  self.next = _.partial(self.paging, 1);
  self.prev = _.partial(self.paging, -1);

  self.toFirstPage = function() {
    self.dataProvider.skip(0);
  };

  self.toLastPage = function() {
    var count = self.count();
    var limit = self.limit();
    var lastPageRows = count % limit || limit;
    var multiplier = (count - lastPageRows) / limit;
    self.dataProvider.skip(limit * multiplier);
  };

  self.start = ko.pureComputed(function() {
      if (self.count() === 0) {
          return 0;
      }
      return self.dataProvider.skip() + 1;
  });

  self.end = ko.pureComputed(function() {
    var endValue = self.dataProvider.skip() + self.dataProvider.limit();
    return self.count() < endValue ? self.count() : endValue;
  });

  self.enablePrevButton = ko.pureComputed(function() {
    return self.count() > self.limit()
      && self.start() > self.limit()
      && !self.disable();
  });

  self.enableNextButton = ko.pureComputed(function() {
    return self.count() > self.limit()
      && self.count() > self.end()
      && !self.disable();
  });

  self.setLimit = function(value) {
    self.limit(value);
  };
};

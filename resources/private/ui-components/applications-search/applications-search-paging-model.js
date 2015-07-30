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
};
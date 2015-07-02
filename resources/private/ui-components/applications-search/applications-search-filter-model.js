LUPAPISTE.HandlersDataProvider = function() {
  "use strict";

  var self = this;

  self.query = ko.observable();

  var data = ko.observable();

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(data) {
      data(data.users);
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data, function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
  });

};


LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

};
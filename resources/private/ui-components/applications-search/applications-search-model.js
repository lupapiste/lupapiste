LUPAPISTE.ApplicationsDataProvider = function() {
  "use strict";

  var self = this;

  self.data = ko.observableArray([]);

  ajax.query("applications-search", {})
  .success(function(data) {
    console.log(data);
    self.data(data.data);
  })
  .call();
};

LUPAPISTE.ApplicationsSearchModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider();

};
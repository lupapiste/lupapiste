LUPAPISTE.HandlersDataProvider = function() {
  "use strict";

  var self = this;

  self.query = ko.observable();

  var data = ko.observable();

  function mapUser(user) {
    var fullName = "";
    if (user) {
      if (user.lastName) { fullName += user.lastName; }
      if (user.firstName && user.lastName) { fullName += "\u00a0"; }
      if (user.firstName) { fullName +=  user.firstName; }
    }
    user.fullName = fullName;
    return user;
  }

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(res) {
      data(_.map(res.users, mapUser));
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.fullName.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
  });
};

LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.organizationTagsDataProvider = null;
  self.handlersDataProvider = null;

  if ( lupapisteApp.models.currentUser.isAuthority() ) {
    self.handlersDataProvider = new LUPAPISTE.HandlersDataProvider();
    // TODO just search single organization tags for now, later do some grouping stuff in autocomplete component
    self.organizationTagsDataProvider = new LUPAPISTE.OrganizationTagsDataProvider(_.last(_.keys(lupapisteApp.models.currentUser.orgAuthz())), self.dataProvider.applicationTags);
  }
};

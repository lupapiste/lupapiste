LUPAPISTE.CompanyTagsService = function() {
  "use strict";
  var self = this;

  var _data = ko.observableArray();

  _data.subscribe( function ()  {
    hub.send( "companyTagsService::changed", {} );
  });


  self.data = ko.pureComputed(function() {
    return _data();
  });

  self.currentCompanyTags = ko.pureComputed(function() {
    var companyId = util.getIn(lupapisteApp.models.currentUser, ["company", "id"]);
    return util.getIn(self.data, [companyId, "tags"]) || [];
  });

  function load(){
    if (lupapisteApp.models.globalAuthModel.ok("company-tags")) {
      ajax.query("company-tags")
        .success(function(res) {
          _data(_.set({}, _.get(res, "company.id"), _.get(res, "company")));
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }

  self.refresh = function() {
    load();
  };

};

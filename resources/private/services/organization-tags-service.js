LUPAPISTE.OrganizationTagsService = function() {
  "use strict";
  var self = this;

  var _data = ko.observable();

  _data.subscribe( function ()  {
    hub.send( "organizationTagsService::changed", {} );
  });


  self.data = ko.pureComputed(function() {
    return _data();
  });

  function load(){
    if (lupapisteApp.models.globalAuthModel.ok("get-organization-tags")) {
      ajax.query("get-organization-tags")
        .success(function(res) {
          _data(res.tags);
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

  self.currentApplicationOrganizationTags = ko.observableArray();

  hub.subscribe( "contextService::enter",
               function( event ) {
                 ajax.query( "application-organization-tags",
                             {id: event.applicationId})
                 .success( function( res ) {
                   self.currentApplicationOrganizationTags( res.tags || [] );
                 })
                 .error( _.noop )
                 .call();
               });
  hub.subscribe( "contextService::leave",
                 function() {
                   self.currentApplicationOrganizationTags( [] );
                 });
};

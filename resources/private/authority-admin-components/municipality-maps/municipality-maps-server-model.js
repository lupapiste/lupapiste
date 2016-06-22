
LUPAPISTE.MunicipalityMapsServerModel = function( params ) {
  "use strict";

  var self = this;

  self.readOnly = params.readOnly;
  self.waiting = params.waiting;
  self.url = ko.observable();
  self.username = ko.observable();
  self.password = ko.observable();

  self.error = params.error;
  self.errorMessageTerm = params.errorMessageTerm;

  ko.computed( function() {
    var server = params.server();
    if(server) {
      self.url( server.url);
      self.username( server.username);
      self.password( server.password);
    }
  });

  self.updateServerDetails = function() {
    params.channel.send( {
      url: _.trim(self.url()),
      username: _.trim(self.username()),
      password: self.password()
    });
  };
};

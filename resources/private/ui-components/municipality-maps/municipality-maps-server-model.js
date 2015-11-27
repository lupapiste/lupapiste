
LUPAPISTE.MunicipalityMapsServerModel = function( params ) {
  "use strict";

  var self = this;

  self.waiting = params.waiting;
  self.url = ko.observable();
  self.username = ko.observable();
  self.password = ko.observable();

  self.error = params.error;

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
      url: self.url(),
      username: self.username(),
      password: self.password()
    });
  };
};

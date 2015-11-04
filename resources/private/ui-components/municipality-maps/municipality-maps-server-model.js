
LUPAPISTE.MunicipalityMapsServerModel = function( params ) {
  "use strict";

  var self = this;

  self.waiting = params.waiting;
  self.url = ko.observable();
  self.username = ko.observable();
  self.password = ko.observable();

  self.error = params.error;

  console.log( "Server params:", params);

  ko.computed( function() {
    console.log( "Server computed");
    var server = params.server();
    if(server) {
      self.url( server.url);
      self.username( server.username);
      self.password( server.password);
    }
  });

  self.updateServerDetails = function() {
    params.server( {
        url: self.url(),
        username: self.username(),
        password: self.password()
    });
    // params.channel.send( {
    //   server: {
    //     url: self.url(),
    //     username: self.username(),
    //     password: self.password()
    //   }
    // });
  };
};

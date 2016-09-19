LUPAPISTE.Open3DMapModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.showButton = self.disposedPureComputed( function() {
    return lupapisteApp.models.applicationAuthModel.ok( "redirect-to-3d-map");
  });

  self.waiting = ko.observable();

  var title = _.template( "<title><%- text %></title>" );
  var h2 = _.template( "<h2><%- text %></h2>" );

  self.open3dMap = function() {
    var newTab = window.open( "", "_blank");
    var waitText = {text: loc( "map.open-3d.wait")};
    newTab.document.write( title( waitText) + h2( waitText));
    ajax.command( "redirect-to-3d-map",
                  {id: lupapisteApp.models.application.id()})
      .pending( self.waiting )
      .success( function( res ) {
        newTab.location = res.location;
      })
      .error( function( res) {
        var errText = {text: loc( res.text)};
        $(newTab.document.head).html( title( errText ));
        $(newTab.document.body).html( h2( errText ));
      })
      .call();
  };
};

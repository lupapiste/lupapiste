LUPAPISTE.Open3DMapModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.showButton = self.disposedPureComputed( function() {
    return lupapisteApp.models.applicationAuthModel.ok( "redirect-to-3d-map");
  });


  var errorTemplate = _.template( "<html><head><title>"
                                  + "<%- text %></title>"
                                  + "<h2><%- text %></h2></head>"
                                  + "</html>");

  self.open3dMap = function() {
    var newTab = window.open( "", "_blank");
    ajax.command( "redirect-to-3d-map",
                  {id: lupapisteApp.models.application.id()})
      .success( function( res ) {
        newTab.location = res.location;
      })
      .error( function( res) {
        newTab.document.write( errorTemplate( {text: loc( res.text)} ));
      })
      .call();
  };

};

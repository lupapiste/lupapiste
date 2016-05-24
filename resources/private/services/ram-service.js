// Rakentamisen Aikainen Muutos (RAM).
LUPAPISTE.RamService = function() {
  "use strict";
  var self = this;

  self.serviceName = "ramService";

  function appId() {
    return lupapisteApp.models.application.id();
  }

  function params( m ) {
    return _.merge( {id: appId()}, m );
  }

  function notifyError( response ) {
    hub.send( "indicator", {style: "negative", message: response.text });
  }

  function openAttachment( attachmentId ) {
    location.assign( "#!/attachment/" + appId() + "/" + attachmentId );
  }

  function newRamAttachment( options ) {
    ajax.command( "create-ram-attachment",
                  params( options ))
      .error( notifyError )
      .success( function( res ) {
        hub.subscribe( "application-model-updated",
                       _.partial( openAttachment, res.attachmentId),
                       true );
        repository.load( appId(), null, null, true );
} )
      .call();
  }

  hub.subscribe( self.serviceName + "::new", newRamAttachment );
};

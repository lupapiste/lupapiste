LUPAPISTE.PremisesUploadModel = function( params ) {
    "use strict";
    var self = this;



    self.file = params.file || ko.observable();
    self.fileInputId = _.uniqueId( "file-input-id-" );
    self.filename = ko.observable();
    self.buttonIcon = 'lupicon-upload';
    self.buttonText = 'huoneistot.premisesUploadButton';
    self.buttonClass = 'btn positive';
    self.testId= "premises-upload-button";



    ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

    var upload = self.upload;
    self.upload = new LUPAPISTE.UploadModel( self, {
        allowMultiple: false,
        readOnly: false,
        badFileHandler: _.noop,
        fileTarget: '/api/raw/upload-premises-data'
    });
    self.upload.init();

    self.disposedSubscribe( self.upload.files, function( files ) {
        var file = _.last(files );
        if( file && !file.attachmentId ) {
            upload.files.push( _.merge( _.clone( file),
                ko.mapping.toJS( params.proxy )));
        }
    });

    self.waiting = self.disposedComputed(function() {
        return self.upload.waiting();
    });

};

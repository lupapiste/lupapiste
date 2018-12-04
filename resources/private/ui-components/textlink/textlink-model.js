// Textual link.
// Parameters [optional]:
// text: Link text. For example: Click [here] to proceed.
// [url]: Url for the embedded link (here in the above example). No
// substitution is done if the url is not given
// [icon]: Icon class.

LUPAPISTE.TextlinkModel = function( params ) {
  "use strict";
  var self = this;

  // Matches [link] with link text as the first group.
  var regex = /\[([^\]]+)\]/;

  var text = _.escapeHTML( params.text );

  var link = regex.exec( text )[1];

  self.html = params.url
    ? _.replace( text, regex, sprintf( "<a href='%s' target='_blank'>%s</a>",
                                       params.url, link ))
    : text;

  self.iconCss = _.size( params.icon )
    ? _.reduce( params.icon,
                function( acc, cls ) {
                  return _.set( acc, cls, true );
                }, {})
    : null;
};

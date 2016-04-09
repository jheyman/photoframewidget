<?php

// Just to enable local testing of the script on server side
if (!isset($_SERVER["HTTP_HOST"])) {
	parse_str($argv[1], $_REQUEST);
}

// get image path from input request data
$path = $_REQUEST['path'];

if ($path != "") {

	// read image from specified path
	$fp = fopen($path, 'rb');

	// send the headers to tell receiver it is image data
	$image_mime = image_type_to_mime_type(exif_imagetype($path));
	header("Content-Type: ".$image_mime);
	header("Content-Length: " . filesize($path));

	// send data and exit immediately to avoid trailing spaces
	fpassthru($fp);
	exit;
}
?>

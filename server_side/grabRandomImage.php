<?php

$path = '/mnt/photo/';
$root = '';
//$root = $_SERVER['DOCUMENT_ROOT'];
 
function getImagesFromDir($path) {
    $images = array();
    if ( $img_dir = @opendir($path) ) {
        while ( false !== ($img_file = readdir($img_dir)) ) {
            // checks for gif, jpg, png
            if ( preg_match("/(\.jpg|\.JPG|\.JPEG|\.gif|\.png)$/", $img_file) ) {
                $images[] = $img_file;
            }
        }
        closedir($img_dir);
    }
    return $images;
}

function getRandomFromArray($ar) {
    mt_srand( (double)microtime() * 1000000 ); // php 4.2+ not needed
    $num = array_rand($ar);
    return $ar[$num];
}


$imgList = getImagesFromDir($root . $path);
$img = getRandomFromArray($imgList);
$imgname = $path.$img;
$fp = fopen($imgname, 'rb');

// send the right headers
$image_mime = image_type_to_mime_type(exif_imagetype($imgname));

header("Content-Type: ".$image_mime);
header("Content-Length: " . filesize($imgname));

// dump the picture and stop the script
fpassthru($fp);
exit;
?>
